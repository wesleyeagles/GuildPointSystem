package com.guild.app.pbstatus;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Status dos PBs da kbase da Cerberus para o painel da DLL (botao da barra, qualquer membro aprovado).
 * <p>
 * Le a pagina a cada 5 s, mas so enquanto alguma DLL pediu a lista no ultimo minuto (painel aberto). Cada leitura
 * que muda alguma coisa ganha um serial novo; a DLL manda o ultimo que tem ({@code since}) e, sem mudanca, recebe
 * so a linha "S" (resposta minuscula, pode perguntar a cada 1 s). Se a kbase falhar, continua com a ultima lista
 * boa e o "S" diz ha quantos segundos ela foi lida.
 */
@Slf4j
@Service
public class PbStatusService {
    static final Duration WANTED = Duration.ofMinutes(1);
    static final Duration APPROVED_CACHE = Duration.ofMinutes(1);

    private final PbStatusSource source;
    private final MemberRepository members;
    Clock clock = Clock.systemUTC();

    record Snapshot(long serial, Instant readAt, List<PbStatusPage.Boss> bosses, String body) {}

    private volatile Snapshot snapshot = new Snapshot(0, Instant.EPOCH, List.of(), "");
    private volatile Instant wantedUntil = Instant.EPOCH;
    private final AtomicBoolean reading = new AtomicBoolean();
    private final Map<String, Instant> approvedUntil = new ConcurrentHashMap<>();

    public PbStatusService(PbStatusSource source, MemberRepository members) {
        this.source = source;
        this.members = members;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    void tick() {
        if (clock.instant().isBefore(wantedUntil)) refresh();
    }

    /** Uma leitura da kbase; false = falhou (fica a lista anterior). */
    synchronized boolean refresh() {
        var html = source.page();
        if (html.isEmpty()) return false;
        List<PbStatusPage.Boss> bosses;
        try {
            bosses = PbStatusPage.dedupe(PbStatusPage.parse(html.get()));
        } catch (RuntimeException e) {
            log.warn("Status de PB da kbase: pagina ilegivel: {}", e.getMessage());
            return false;
        }
        if (bosses.isEmpty()) return false;  // pagina de erro / layout novo: melhor a lista velha que nenhuma
        Snapshot old = snapshot;
        Instant now = clock.instant();
        snapshot = bosses.equals(old.bosses()) ? new Snapshot(old.serial(), now, old.bosses(), old.body())
                : new Snapshot(old.serial() + 1, now, bosses, body(bosses));
        return true;
    }

    /**
     * Texto UTF-8 para a DLL. Primeira linha "S TAB serial TAB segundos desde a leitura"; se {@code since} ja e o
     * serial atual, so ela. Senao, por mapa "M TAB nome" e por boss
     * "B TAB codigo TAB nome russo TAB L|D|K TAB quem matou TAB guild TAB nascimento normal (unix s, 0 = sem) TAB pb de guild 0/1
     * TAB nascimento extra (unix s, 0 = sem; 14:00 de Brasilia, ver PbStatusPage.EXTRA_SLOT)".
     */
    @Transactional(readOnly = true)
    public String forGame(String character, Long since) {
        if (!approved(character)) throw new AppException("Personagem sem conta aprovada no site.", HttpStatus.FORBIDDEN);
        Instant now = clock.instant();
        boolean first = !now.isBefore(wantedUntil);
        wantedUntil = now.plus(WANTED);
        // Primeiro painel aberto depois de um tempo: le ja, em segundo plano (a kbase as vezes leva 10 s ou mais).
        if ((first || snapshot.serial() == 0) && reading.compareAndSet(false, true))
            CompletableFuture.runAsync(() -> { try { refresh(); } finally { reading.set(false); } });
        Snapshot s = snapshot;
        long age = s.serial() == 0 ? -1 : Math.max(0, Duration.between(s.readAt(), now).toSeconds());
        String head = "S\t" + s.serial() + "\t" + age + "\n";
        return since != null && since == s.serial() ? head : head + s.body();
    }

    private static String body(List<PbStatusPage.Boss> bosses) {
        var sb = new StringBuilder();
        String map = null;
        for (var b : bosses) {
            if (!b.map().equals(map)) { map = b.map(); sb.append("M\t").append(clean(map)).append('\n'); }
            sb.append("B\t").append(b.code()).append('\t').append(clean(b.name())).append('\t')
              .append(switch (b.state()) { case LIVE -> 'L'; case DEAD -> 'D'; case KILLED -> 'K'; }).append('\t')
              .append(clean(b.killer())).append('\t').append(clean(b.guild())).append('\t')
              .append(b.respawn() == null ? 0 : b.respawn().getEpochSecond()).append('\t')
              .append(b.guildPb() ? 1 : 0).append('\t')
              .append(b.extra() == null ? 0 : b.extra().getEpochSecond()).append('\n');
        }
        return sb.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** A DLL pergunta a cada 1 s: a conferencia do membro fica 1 min em memoria. */
    private boolean approved(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return false;
        String key = character.strip();
        Instant now = clock.instant(), until = approvedUntil.get(key);
        if (until != null && until.isAfter(now)) return true;
        boolean ok = members.findByGameCharacter(key).stream().anyMatch(m -> m.getStatus() == MemberStatus.APROVADO
                && m.isProfileComplete() && m.getDeletedAt() == null && m.getSuspendedAt() == null);
        if (ok) {
            if (approvedUntil.size() > 2000) approvedUntil.clear();
            approvedUntil.put(key, now.plus(APPROVED_CACHE));
        }
        return ok;
    }
}
