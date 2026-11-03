package com.guild.app.raid.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.raid.service.RaidService.RaidView;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Status ao vivo da raid, só em memória (nada vai para o banco). A DLL de cada integrante envia
 * o próprio HP/FP/SP a cada segundo e recebe, na mesma resposta, o de todos da raid. O site
 * recebe as mudanças pelo tópico /topic/raids/{id}/live, no máximo uma vez por segundo.
 * Como os outros envios da DLL, o personagem é identificado pelo nome.
 */
@Service
@RequiredArgsConstructor
public class RaidLiveService {
    /** Sem envio há mais que isso: o integrante aparece sem DLL/fora do jogo. */
    static final Duration ONLINE = Duration.ofSeconds(5);
    static final Duration KEEP = Duration.ofMinutes(2);
    static final int MAX_VALUE = 100_000_000;
    static final int MAX_EFFECTS = 32;
    static final int EFFECT_NAME_MAX = 32;

    private final RaidService raids;
    private final MemberRepository members;
    private final WebSocketPublisher ws;
    Clock clock = Clock.systemUTC();

    public record Vitals(int level, int hp, int hpMax, int fp, int fpMax, int sp, int spMax) {}
    /** Buff/debuff ativo, lido pela DLL da lista de efeitos do personagem. type: 0 skill, 1 skill de classe, 2 force. */
    public record Effect(int type, int index, int level, boolean debuff, String name) {}
    record Report(Vitals vitals, List<Effect> effects) {}
    private record Live(Vitals vitals, List<Effect> effects, Instant at) {}
    /** Para o site: ageMs = há quanto tempo veio o último envio (-1 = nunca). */
    public record LiveMember(Long memberId, int level, int hp, int hpMax, int fp, int fpMax, int sp, int spMax, long ageMs,
                             List<Effect> effects) {}
    private record Roster(long version, Map<Long, RaidView> byMember, Map<Long, RaidView> byRaid) {}

    private final Map<Long, Live> live = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private volatile Roster roster = new Roster(-1, Map.of(), Map.of());

    /**
     * Corpo da DLL: "level<TAB>hp<TAB>hpMax<TAB>fp<TAB>fpMax<TAB>sp<TAB>spMax" e, a partir da 1.7.0, uma segunda
     * linha "E<TAB>efeitos" com "tipo,indice,nivel,B|D,nome" separados por ';' (vazia = nenhum).
     */
    static Report parse(String body) {
        if (body == null) throw invalid();
        String[] lines = body.strip().split("\r?\n", -1);
        if (lines.length > 2) throw invalid();
        List<Effect> effects = List.of();
        if (lines.length == 2) {
            // "E" sozinho: lista vazia (o strip() do corpo leva o TAB final de "E<TAB>").
            if (!lines[1].equals("E") && !lines[1].startsWith("E\t")) throw invalid();
            effects = lines[1].length() <= 2 ? List.of() : parseEffects(lines[1].substring(2));
        }
        return new Report(parseVitals(lines[0]), effects);
    }

    static List<Effect> parseEffects(String text) {
        if (text.isEmpty()) return List.of();
        String[] items = text.split(";", -1);
        if (items.length > MAX_EFFECTS) throw invalid();
        var out = new ArrayList<Effect>();
        for (String item : items) {
            String[] f = item.split(",", 5);
            if (f.length != 5 || !(f[3].equals("B") || f[3].equals("D"))) throw invalid();
            int type = number(f[0], 15), index = number(f[1], 0xFFFF), level = number(f[2], 255);
            String name = f[4];
            if (name.length() > EFFECT_NAME_MAX || !name.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) throw invalid();
            out.add(new Effect(type, index, level, f[3].equals("D"), name.strip()));
        }
        return List.copyOf(out);
    }
    private static int number(String text, int max) {
        try {
            int v = Integer.parseInt(text);
            if (v < 0 || v > max) throw invalid();
            return v;
        } catch (NumberFormatException e) { throw invalid(); }
    }

    static Vitals parseVitals(String line) {
        String[] parts = line.strip().split("\t", -1);
        if (parts.length != 7) throw invalid();
        int[] v = new int[7];
        for (int i = 0; i < 7; i++) {
            try { v[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { throw invalid(); }
            if (v[i] < 0 || v[i] > MAX_VALUE) throw invalid();
        }
        if (v[0] < 1 || v[0] > 200 || v[2] <= 0 || v[1] > v[2] || v[3] > v[4] || v[5] > v[6]) throw invalid();
        return new Vitals(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
    }
    private static AppException invalid() { return new AppException("Status inválido.", HttpStatus.BAD_REQUEST); }

    /**
     * Guarda o status do personagem e devolve a raid dele em texto (vazio = não está em raid).
     * Primeira linha "R<TAB>id<TAB>nome"; depois um integrante por linha:
     * "subgrupo<TAB>nick<TAB>level<TAB>hp<TAB>hpMax<TAB>fp<TAB>fpMax<TAB>sp<TAB>spMax<TAB>marcas<TAB>idade",
     * marcas = L (líder da raid), P (líder do subgrupo) ou "-"; idade em segundos (-1 = sem envio).
     */
    public Optional<String> report(String character, String body) {
        return report(character, body, false);
    }

    /** withEffects: a DLL pediu (fx=1) a 12a coluna, com os efeitos de cada integrante. */
    public Optional<String> report(String character, String body, boolean withEffects) {
        Report report = parse(body);
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        Instant now = clock.instant();
        var current = roster();
        for (Member member : members.findByGameCharacter(character)) {
            if (!RaidService.eligible(member)) continue;
            RaidView raid = current.byMember().get(member.getId());
            if (raid == null) continue;
            live.put(member.getId(), new Live(report.vitals(), report.effects(), now));
            dirty.add(raid.id());
            return Optional.of(render(raid, now, withEffects));
        }
        return Optional.empty();
    }

    public List<LiveMember> forRaid(Long raidId, MemberPrincipal actor) {
        Member viewer = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!RaidService.eligible(viewer)) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        RaidView raid = roster().byRaid().get(raidId);
        if (raid == null) throw new AppException("Raid não encontrada", HttpStatus.NOT_FOUND);
        return liveMembers(raid, clock.instant());
    }

    /** Uma mensagem por raid que recebeu envio desde a última rodada. */
    @Scheduled(fixedDelay = 1000)
    public void broadcast() {
        if (dirty.isEmpty()) return;
        var current = roster();
        Instant now = clock.instant();
        for (Iterator<Long> it = dirty.iterator(); it.hasNext(); ) {
            Long raidId = it.next();
            it.remove();
            RaidView raid = current.byRaid().get(raidId);
            if (raid != null) ws.publishRaidLive(raidId, liveMembers(raid, now));
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void prune() {
        Instant limit = clock.instant().minus(KEEP);
        live.values().removeIf(l -> l.at().isBefore(limit));
    }

    private List<LiveMember> liveMembers(RaidView raid, Instant now) {
        return raid.members().stream().map(m -> {
            Live l = live.get(m.id());
            if (l == null) return new LiveMember(m.id(), m.level(), 0, 0, 0, 0, 0, 0, -1, List.of());
            Vitals v = l.vitals();
            return new LiveMember(m.id(), v.level(), v.hp(), v.hpMax(), v.fp(), v.fpMax(), v.sp(), v.spMax(),
                Duration.between(l.at(), now).toMillis(), l.effects());
        }).toList();
    }

    private String render(RaidView raid, Instant now, boolean withEffects) {
        var out = new StringBuilder("R\t").append(raid.id()).append('\t').append(clean(raid.name())).append('\n');
        for (var m : raid.members()) {
            Live l = live.get(m.id());
            Vitals v = l != null ? l.vitals() : new Vitals(m.level(), 0, 0, 0, 0, 0, 0);
            String flags = (m.raidLeader() ? "L" : "") + (m.groupLeader() ? "P" : "");
            long age = l != null ? Duration.between(l.at(), now).toSeconds() : -1;
            out.append(m.group()).append('\t').append(clean(m.nickname())).append('\t').append(v.level())
                .append('\t').append(v.hp()).append('\t').append(v.hpMax())
                .append('\t').append(v.fp()).append('\t').append(v.fpMax())
                .append('\t').append(v.sp()).append('\t').append(v.spMax())
                .append('\t').append(flags.isEmpty() ? "-" : flags).append('\t').append(age);
            if (withEffects) out.append('\t').append(l != null ? effectsField(l.effects()) : "");
            out.append('\n');
        }
        return out.toString();
    }
    /** "tipo,indice,nivel,B|D,nome" separados por ';' (o formato que a DLL envia). */
    static String effectsField(List<Effect> effects) {
        var out = new StringJoiner(";");
        for (Effect e : effects)
            out.add(e.type() + "," + e.index() + "," + e.level() + "," + (e.debuff() ? "D" : "B") + "," + e.name());
        return out.toString();
    }
    private static String clean(String text) { return text.replaceAll("[\\p{Cntrl}]", " "); }

    /** Escalação em cache; recarrega quando o RaidService confirma alguma mudança. */
    private Roster roster() {
        long version = raids.version();
        var current = roster;
        if (current.version() == version) return current;
        synchronized (this) {
            if (roster.version() == version) return roster;
            var byMember = new HashMap<Long, RaidView>();
            var byRaid = new HashMap<Long, RaidView>();
            for (var raid : raids.all()) {
                byRaid.put(raid.id(), raid);
                for (var m : raid.members()) byMember.put(m.id(), raid);
            }
            roster = new Roster(version, Map.copyOf(byMember), Map.copyOf(byRaid));
            return roster;
        }
    }
}
