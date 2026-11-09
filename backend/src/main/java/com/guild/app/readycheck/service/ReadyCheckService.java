package com.guild.app.readycheck.service;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Check de "Pronto?" antes do BD, como o ready check do WoW, todo dentro do jogo. A DLL do líder da PT lê a
 * PT do jogo e envia os nicks (líder primeiro); a DLL de cada integrante consulta o site enquanto está em PT,
 * mostra a janela e devolve Pronto/Não. O site só repassa: tudo fica em memória por alguns segundos, sem banco
 * (só a conferência do líder no início).
 * Estados por integrante: '?' esperando, 'Y' pronto, 'N' não, 'A' não respondeu a tempo (sem DLL ou AFK).
 */
@Service
public class ReadyCheckService {
    public static final Duration ANSWER = Duration.ofSeconds(30);
    /** Depois do resultado, as DLLs ainda mostram a janela com os ✔/✖ por este tempo. */
    static final Duration SHOW = Duration.ofSeconds(8);
    /** Um check novo do mesmo líder só depois disso (clique repetido no botão). */
    static final Duration COOLDOWN = Duration.ofSeconds(3);
    static final int MAX_MEMBERS = 8;
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_-]{2,16}");

    private final MemberRepository members;
    private final Map<Long, Check> checks = new ConcurrentHashMap<>();
    private final Map<String, Long> byNick = new ConcurrentHashMap<>();  // nick em minúsculas -> check atual
    private long nextId = 1;
    Clock clock = Clock.systemUTC();

    public ReadyCheckService(MemberRepository members) {
        this.members = members;
    }

    private static final class Check {
        final long id;
        final String leader;
        final Instant startedAt;
        final LinkedHashMap<String, Character> states = new LinkedHashMap<>();  // nick como veio do jogo -> estado
        Instant doneAt;

        Check(long id, String leader, Instant startedAt) {
            this.id = id;
            this.leader = leader;
            this.startedAt = startedAt;
        }
    }

    /**
     * Líder da PT inicia: corpo = um nick por linha, o líder primeiro (o próprio personagem). Só personagem de
     * membro aprovado; pelo menos mais um integrante. Devolve o check no formato de {@link #current}.
     */
    @Transactional(readOnly = true)
    public String start(String character, String body) {
        if (!valid(character)) throw new AppException("Personagem inválido.", HttpStatus.BAD_REQUEST);
        var nicks = parse(body);
        if (nicks.isEmpty() || !nicks.get(0).equalsIgnoreCase(character.strip()))
            throw new AppException("Só o líder da PT pode chamar o check.", HttpStatus.FORBIDDEN);
        if (nicks.size() < 2) throw new AppException("A PT precisa de mais alguém.", HttpStatus.BAD_REQUEST);
        boolean member = members.findByGameCharacter(character.strip()).stream().anyMatch(ReadyCheckService::eligible);
        if (!member) throw new AppException("Personagem não é de membro aprovado.", HttpStatus.FORBIDDEN);
        synchronized (this) {
            Instant now = clock.instant();
            Check previous = find(nicks.get(0));
            if (previous != null && previous.leader.equalsIgnoreCase(nicks.get(0)) && previous.doneAt == null &&
                now.isBefore(previous.startedAt.plus(COOLDOWN)))
                return view(previous, now);
            Check check = new Check(nextId++, nicks.get(0), now);
            for (String nick : nicks) check.states.put(nick, '?');
            check.states.put(nicks.get(0), 'Y');  // quem chama já está pronto
            checks.put(check.id, check);
            for (String nick : nicks) byNick.put(key(nick), check.id);
            return view(check, now);
        }
    }

    /**
     * Check atual deste personagem, ou null: "id&lt;TAB&gt;líder&lt;TAB&gt;segundos restantes&lt;TAB&gt;OPEN|DONE" e uma
     * linha "nick&lt;TAB&gt;estado" por integrante, na ordem da PT. Sem nenhum check aberto não procura nada.
     */
    public String current(String character) {
        if (checks.isEmpty() || !valid(character)) return null;
        synchronized (this) {
            Check check = find(character);
            if (check == null) return null;
            Instant now = clock.instant();
            settle(check, now);
            if (expired(check, now)) { remove(check); return null; }
            return view(check, now);
        }
    }

    /** Resposta de um integrante: "Y" pronto, qualquer outra coisa = não. Só enquanto o check está aberto. */
    public String answer(long id, String character, String body) {
        if (!valid(character)) throw new AppException("Personagem inválido.", HttpStatus.BAD_REQUEST);
        synchronized (this) {
            Check check = checks.get(id);
            Instant now = clock.instant();
            if (check == null || expired(check, now)) throw new AppException("Check encerrado.", HttpStatus.GONE);
            settle(check, now);
            String nick = check.states.keySet().stream().filter(n -> n.equalsIgnoreCase(character.strip())).findFirst()
                    .orElseThrow(() -> new AppException("Você não está neste check.", HttpStatus.FORBIDDEN));
            if (check.doneAt == null && check.states.get(nick) == '?') {
                check.states.put(nick, body != null && body.strip().equalsIgnoreCase("Y") ? 'Y' : 'N');
                settle(check, now);
            }
            return view(check, now);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void sweep() {
        Instant now = clock.instant();
        for (Check check : List.copyOf(checks.values())) {
            settle(check, now);
            if (expired(check, now)) remove(check);
        }
    }

    /** Fecha o check quando todos responderam ou o tempo acabou (quem não respondeu vira 'A'). */
    private void settle(Check check, Instant now) {
        if (check.doneAt != null) return;
        boolean waiting = check.states.containsValue('?');
        Instant deadline = check.startedAt.plus(ANSWER);
        if (waiting && now.isBefore(deadline)) return;
        check.states.replaceAll((nick, state) -> state == '?' ? 'A' : state);
        check.doneAt = waiting ? deadline : now;
    }

    private boolean expired(Check check, Instant now) {
        return check.doneAt != null && !now.isBefore(check.doneAt.plus(SHOW));
    }

    private void remove(Check check) {
        checks.remove(check.id);
        for (String nick : check.states.keySet()) byNick.remove(key(nick), check.id);
    }

    private Check find(String nick) {
        Long id = byNick.get(key(nick));
        return id == null ? null : checks.get(id);
    }

    private static String view(Check check, Instant now) {
        long left = check.doneAt != null ? 0
                : Math.max(0, Duration.between(now, check.startedAt.plus(ANSWER)).toMillis() + 999) / 1000;
        var out = new StringBuilder();
        out.append(check.id).append('\t').append(check.leader).append('\t').append(left).append('\t')
                .append(check.doneAt == null ? "OPEN" : "DONE").append('\n');
        check.states.forEach((nick, state) -> out.append(nick).append('\t').append(state).append('\n'));
        return out.toString();
    }

    static List<String> parse(String body) {
        var out = new ArrayList<String>();
        if (body == null) return out;
        var seen = new HashSet<String>();
        for (String line : body.split("\n")) {
            String nick = line.strip();
            if (!NICK.matcher(nick).matches() || !seen.add(key(nick))) continue;
            out.add(nick);
            if (out.size() == MAX_MEMBERS) break;
        }
        return out;
    }

    private static boolean valid(String character) {
        return character != null && NICK.matcher(character.strip()).matches();
    }

    private static String key(String nick) {
        return nick.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
                && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }
}
