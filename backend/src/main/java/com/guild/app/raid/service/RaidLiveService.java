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
    /** Resposta à DLL de um integrante oculto: "continue enviando, sem raid para mostrar". */
    static final String OBSERVED = "O\n";
    static final int EFFECT_NAME_MAX = 32;
    /** Bandeira no mapa: some sozinha depois disso. */
    static final Duration FLAG_TTL = Duration.ofMinutes(2);
    /** Seta da raid (alvo marcado pelo líder): some sozinha depois disso, se ninguém tirar antes. */
    static final Duration MARK_TTL = Duration.ofMinutes(10);

    private final RaidService raids;
    private final MemberRepository members;
    private final WebSocketPublisher ws;
    private final RaidAccessService access;
    Clock clock = Clock.systemUTC();

    public record Vitals(int level, int hp, int hpMax, int fp, int fpMax, int sp, int spMax) {}
    /**
     * Buff/debuff ativo, lido pela DLL da lista de efeitos do personagem. type: 0 skill, 1 force, 2 skill de classe.
     * icon: célula na folha de ícones do tipo (/effect-icons/{skill,force,class}.png); -1 = desconhecido.
     */
    public record Effect(int type, int index, int level, boolean debuff, int icon, String name) {}
    /** Onde o personagem está: índice do mapa do jogo e posição no mundo (X e Z), como o mapa do jogo usa. */
    public record Position(int map, int x, int z) {}
    /** Pedido de bandeira que veio no envio: colocar (position) ou tirar (position null). */
    record FlagAction(Position position) {}
    /**
     * Seta da raid pedida no envio (Ctrl+Shift+clique do líder): kind = tipo do objeto no jogo (o do widget de alvo:
     * jogador, monstro...) e serial dele; null = tirar a seta.
     */
    record TargetAction(Integer kind, Long serial) {}
    record Report(Vitals vitals, List<Effect> effects, Position position, FlagAction flag, TargetAction target) {}
    private record Mark(int kind, long serial, Instant at) {}
    private record Live(Vitals vitals, List<Effect> effects, Position position, Instant at) {}
    private record Flag(Position position, Instant at) {}
    /** Para o site: ageMs = há quanto tempo veio o último envio (-1 = nunca). */
    public record LiveMember(Long memberId, int level, int hp, int hpMax, int fp, int fpMax, int sp, int spMax, long ageMs,
                             List<Effect> effects) {}
    private record Roster(long version, Map<Long, RaidView> byMember, Map<Long, RaidView> byRaid) {}

    private final Map<Long, Live> live = new ConcurrentHashMap<>();
    /** Uma bandeira por integrante (a nova substitui a anterior), só em memória. */
    private final Map<Long, Flag> flags = new ConcurrentHashMap<>();
    /** Seta da raid: uma por raid (id da raid -> alvo), só em memória. */
    private final Map<Long, Mark> marks = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private volatile Roster roster = new Roster(-1, Map.of(), Map.of());

    /**
     * Corpo da DLL: "level<TAB>hp<TAB>hpMax<TAB>fp<TAB>fpMax<TAB>sp<TAB>spMax" e, a partir da 1.7.0, uma segunda
     * linha "E<TAB>efeitos" com "tipo,indice,nivel,B|D,icone,nome" separados por ';' (vazia = nenhum). O nome
     * nunca tem vírgula, então também vale o formato sem ícone ("tipo,indice,nivel,B|D,nome").
     * Depois (DLL com fx=2), uma linha "L<TAB>mapa<TAB>x<TAB>z" com o índice do mapa e a posição no mundo.
     * Só no envio em que o jogador marca (DLL com fx=3), uma linha "F<TAB>mapa<TAB>x<TAB>z" coloca a bandeira
     * dele no mapa e "F<TAB>-" tira. Só no envio em que o líder marca (DLL com fx=4), "T<TAB>tipo<TAB>serial" põe a
     * seta da raid nesse objeto do jogo e "T<TAB>-" tira.
     */
    static Report parse(String body) {
        if (body == null) throw invalid();
        String[] lines = body.strip().split("\r?\n", -1);
        if (lines.length > 5) throw invalid();
        List<Effect> effects = List.of();
        Position position = null;
        FlagAction flag = null;
        TargetAction target = null;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            // "E" sozinho: lista vazia (o strip() do corpo leva o TAB final de "E<TAB>").
            if (line.equals("E") || line.startsWith("E\t")) effects = line.length() <= 2 ? List.of() : parseEffects(line.substring(2));
            else if (line.startsWith("L\t")) position = parsePosition(line.substring(2));
            else if (line.equals("F\t-")) flag = new FlagAction(null);
            else if (line.startsWith("F\t")) flag = new FlagAction(parsePosition(line.substring(2)));
            else if (line.equals("T\t-")) target = new TargetAction(null, null);
            else if (line.startsWith("T\t")) target = parseTarget(line.substring(2));
            else throw invalid();
        }
        return new Report(parseVitals(lines[0]), effects, position, flag, target);
    }

    static Position parsePosition(String text) {
        String[] f = text.split("\t", -1);
        if (f.length != 3) throw invalid();
        try {
            int map = Integer.parseInt(f[0]), x = Integer.parseInt(f[1]), z = Integer.parseInt(f[2]);
            if (map < 0 || map > 1000 || Math.abs(x) > 1_000_000 || Math.abs(z) > 1_000_000) throw invalid();
            return new Position(map, x, z);
        } catch (NumberFormatException e) { throw invalid(); }
    }

    static TargetAction parseTarget(String text) {
        String[] f = text.split("\t", -1);
        if (f.length != 2) throw invalid();
        try {
            int kind = Integer.parseInt(f[0]);
            long serial = Long.parseLong(f[1]);
            if (kind < 0 || kind > 15 || serial < 0 || serial > 0xFFFFFFFEL) throw invalid();
            return new TargetAction(kind, serial);
        } catch (NumberFormatException e) { throw invalid(); }
    }

    static List<Effect> parseEffects(String text) {
        if (text.isEmpty()) return List.of();
        String[] items = text.split(";", -1);
        if (items.length > MAX_EFFECTS) throw invalid();
        var out = new ArrayList<Effect>();
        for (String item : items) {
            String[] f = item.split(",", 6);
            if ((f.length != 5 && f.length != 6) || !(f[3].equals("B") || f[3].equals("D"))) throw invalid();
            int type = number(f[0], 15), index = number(f[1], 0xFFFF), level = number(f[2], 255);
            int icon = f.length == 6 ? (f[4].equals("-1") ? -1 : number(f[4], 0xFFF)) : -1;
            String name = f[f.length - 1];
            if (name.length() > EFFECT_NAME_MAX || !name.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) throw invalid();
            out.add(new Effect(type, index, level, f[3].equals("D"), icon, name.strip()));
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
     * marcas = L (líder da raid), P (líder do subgrupo), 1..8 (marca de raid, só com fx=1) ou "-"; idade em s.
     * Só vai quem está no jogo (enviou nos últimos {@link #ONLINE}): o painel do jogo não mostra quem está fora.
     */
    public Optional<String> report(String character, String body) {
        return report(character, body, false);
    }

    public Optional<String> report(String character, String body, boolean withEffects) {
        return report(character, body, withEffects ? 1 : 0);
    }

    /**
     * fx: o que a DLL entende na resposta. 1 (1.7.0+): marca de raid e 12a coluna com os efeitos;
     * 2: também a 13a coluna com o mapa e a posição de cada integrante ("mapa,x,z"; vazia = sem posição).
     * 3 (1.8.1+): também as linhas "F" das bandeiras no mapa, depois dos integrantes.
     */
    public Optional<String> report(String character, String body, int fx) {
        boolean withEffects = fx >= 1;
        Report report = parse(body);
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        Instant now = clock.instant();
        var current = roster();
        for (Member member : members.findByGameCharacter(character)) {
            if (!RaidService.eligible(member)) continue;
            RaidView raid = current.byMember().get(member.getId());
            if (raid == null) continue;
            live.put(member.getId(), new Live(report.vitals(), report.effects(), report.position(), now));
            dirty.add(raid.id());
            // Oculto: continua enviando a cada 1 s (DLL 1.7.0+ recebe "O"), mas sem raid para mostrar.
            // A DLL 1.6.0 recebe 204 e passa a enviar a cada 10 s.
            boolean hidden = raid.members().stream().anyMatch(m -> m.id().equals(member.getId()) && m.hidden());
            if (hidden) return withEffects ? Optional.of(OBSERVED) : Optional.empty();
            // Oculto não marca o mapa (nem vê a raid); qualquer outro integrante coloca ou tira a própria bandeira.
            if (report.flag() != null) {
                if (report.flag().position() == null) flags.remove(member.getId());
                else flags.put(member.getId(), new Flag(report.flag().position(), now));
            }
            // Só o líder da raid põe ou tira a seta (de outro integrante o pedido é ignorado).
            if (report.target() != null && member.getId().equals(raid.leaderId())) {
                if (report.target().kind() == null) marks.remove(raid.id());
                else marks.put(raid.id(), new Mark(report.target().kind(), report.target().serial(), now));
            }
            return Optional.of(render(raid, now, fx));
        }
        return Optional.empty();
    }

    public List<LiveMember> forRaid(Long raidId, MemberPrincipal actor) {
        Member viewer = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!RaidService.eligible(viewer)) throw new AppException("Acesso negado", HttpStatus.FORBIDDEN);
        access.requireView(viewer);
        RaidView raid = roster().byRaid().get(raidId);
        if (raid == null) throw new AppException("Raid não encontrada", HttpStatus.NOT_FOUND);
        var all = liveMembers(raid, clock.instant());
        return access.canManageRaid(viewer, raid.leaderId()) ? all : visibleOnly(raid, all);
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
            if (raid == null) continue;
            var all = liveMembers(raid, now);
            // Tópico de todos sem os ocultos; o completo só para quem gerencia (WebSocketAuthInterceptor).
            ws.publishRaidLive(raidId, visibleOnly(raid, all));
            ws.publishRaidLiveAll(raidId, all);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void prune() {
        Instant limit = clock.instant().minus(KEEP);
        live.values().removeIf(l -> l.at().isBefore(limit));
        Instant markLimit = clock.instant().minus(MARK_TTL);
        marks.values().removeIf(m -> m.at().isBefore(markLimit));
    }

    private static List<LiveMember> visibleOnly(RaidView raid, List<LiveMember> all) {
        var hidden = raid.members().stream().filter(RaidService.RaidMemberView::hidden).map(RaidService.RaidMemberView::id)
            .collect(java.util.stream.Collectors.toSet());
        return hidden.isEmpty() ? all : all.stream().filter(m -> !hidden.contains(m.memberId())).toList();
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

    private String render(RaidView raid, Instant now, int fx) {
        boolean withEffects = fx >= 1;
        var out = new StringBuilder("R\t").append(raid.id()).append('\t').append(clean(raid.name())).append('\n');
        for (var m : raid.members()) {
            Live l = live.get(m.id());
            if (l == null || Duration.between(l.at(), now).compareTo(ONLINE) > 0) continue;  // fora do jogo
            Vitals v = l.vitals();
            // A marca de raid (1..8) vai junto das marcas, so para a DLL 1.7.0+ (a 1.6.0 recusaria o digito).
            String flags = (m.raidLeader() ? "L" : "") + (m.groupLeader() ? "P" : "")
                + (withEffects && m.mark() != null ? String.valueOf(m.mark()) : "");
            long age = Duration.between(l.at(), now).toSeconds();
            out.append(m.group()).append('\t').append(clean(m.nickname())).append('\t').append(v.level())
                .append('\t').append(v.hp()).append('\t').append(v.hpMax())
                .append('\t').append(v.fp()).append('\t').append(v.fpMax())
                .append('\t').append(v.sp()).append('\t').append(v.spMax())
                .append('\t').append(flags.isEmpty() ? "-" : flags).append('\t').append(age);
            if (withEffects) out.append('\t').append(effectsField(l.effects()));
            if (fx >= 2) {
                Position p = l.position();
                out.append('\t').append(p != null ? p.map() + "," + p.x() + "," + p.z() : "");
            }
            out.append('\n');
        }
        // fx=3: bandeiras dos integrantes visíveis, "F<TAB>nick<TAB>mapa,x,z<TAB>idade em segundos".
        if (fx >= 3) {
            for (var m : raid.members()) {
                Flag f = flags.get(m.id());
                if (f == null || m.hidden()) continue;
                long age = Duration.between(f.at(), now).toSeconds();
                if (age >= FLAG_TTL.toSeconds()) { flags.remove(m.id(), f); continue; }
                Position p = f.position();
                out.append("F\t").append(clean(m.nickname())).append('\t')
                    .append(p.map()).append(',').append(p.x()).append(',').append(p.z()).append('\t').append(age).append('\n');
            }
        }
        // fx=4: a seta da raid, "T<TAB>tipo<TAB>serial<TAB>idade em segundos" (no máximo uma).
        if (fx >= 4) {
            Mark mark = marks.get(raid.id());
            if (mark != null) {
                long age = Duration.between(mark.at(), now).toSeconds();
                if (age >= MARK_TTL.toSeconds()) marks.remove(raid.id(), mark);
                else out.append("T\t").append(mark.kind()).append('\t').append(mark.serial()).append('\t').append(age).append('\n');
            }
        }
        return out.toString();
    }
    /** "tipo,indice,nivel,B|D,icone,nome" separados por ';' (o formato que a DLL 1.7.0 envia e entende). */
    static String effectsField(List<Effect> effects) {
        var out = new StringJoiner(";");
        for (Effect e : effects)
            out.add(e.type() + "," + e.index() + "," + e.level() + "," + (e.debuff() ? "D" : "B") + "," + e.icon() + "," + e.name());
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
