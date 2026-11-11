package com.guild.app.cw.service;

import com.guild.app.characterstatus.entity.CharacterStatus;
import com.guild.app.characterstatus.repository.CharacterStatusRepository;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.ObjectiveType;
import com.guild.app.common.enums.PointsModality;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.event.entity.EventClaim;
import com.guild.app.event.repository.EventClaimRepository;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.objective.entity.Objective;
import com.guild.app.objective.repository.ObjectiveRepository;
import com.guild.app.points.service.PointsService;
import com.guild.app.schedule.GameEventDefinitions;
import com.guild.app.shout.service.ShoutService;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Presença e pontuação de CW.
 * <ul>
 *   <li>Cada envio de status da DLL perto e durante a CW vira uma amostra (está em Crag Mind Field? parado desde?).</li>
 *   <li>O tempo entre duas amostras seguidas em Crag conta como presença; se o personagem estava AFK (parado na
 *       mesma coordenada há mais de 1 min 30 s sem EXP), o trecho parado inteiro vai para AFK, desde o começo.
 *       Indo e voltando entre os mesmos pontos por 5 min sem skill nem EXP (bot) também é AFK.</li>
 *   <li>Saiu de Crag (morreu e nasceu em outro mapa, TP) e voltou em até 2 min: não perde nada. Mais que isso, o
 *       tempo em outro mapa vai todo para "Fora da CW".</li>
 *   <li>A CW começa no horário fixo e acaba quando a DLL de algum membro vê "Due to the result of Chip War" no chat
 *       (ou quando um Admin marca o fim). 2 min depois do fim, quem ficou 60% da CW em Crag sem AFK recebe o
 *       objetivo "CW" ou, em dia de PB Major, "CW Major" (os pontos entram sozinhos).</li>
 *   <li>10 min antes de cada CW, quem está em Crag (AFK ou não) recebe 2 pontos.</li>
 *   <li>Cada membro pontua em no máximo 2 CWs por dia (e recebe o bônus de 10 min antes no máximo 2 vezes).</li>
 * </ul>
 * O local vem da DLL sem login (como no resto da sincronização): a guilda aceitou esse risco.
 * A tabela de CWs (com a % de cada um) é de todos os membros; marcar o fim à mão é de Administrador+.
 */
@Service
@RequiredArgsConstructor
public class CwAttendanceService {

    /** Nome do mapa como a DLL envia. */
    public static final String CW_MAP = "Crag Mind Field";
    /** Na CW, parado (mesma coordenada, sem EXP) por mais que isso = AFK (no "Pontos de RG" são 3 min). */
    public static final Duration AFK_AFTER = Duration.ofSeconds(90);
    /** Amostras mais distantes que isso (DLL fechou, caiu, carregando): o intervalo não conta. */
    public static final Duration MAX_GAP = Duration.ofSeconds(150);
    /**
     * Saiu de Crag (morreu e nasceu em outro mapa) e voltou em até isso, do primeiro envio fora ao primeiro de
     * volta: o tempo fora conta como presença. Mais que isso: o tempo fora inteiro é "Fora da CW".
     */
    public static final Duration OUTSIDE_GRACE = Duration.ofMinutes(2);
    /** Amostras guardadas a partir de um pouco antes do início, para o primeiro minuto da CW contar. */
    public static final Duration SAMPLE_LEAD = Duration.ofMinutes(5);
    /** Aviso de fim só vale depois disso (um aviso de CW anterior atrasado não fecha a nova). */
    public static final Duration MIN_LENGTH = Duration.ofMinutes(5);
    /** Espera depois do fim para chegarem os últimos envios antes de pontuar. */
    public static final Duration AWARD_DELAY = Duration.ofMinutes(2);
    /** Fração da CW em Crag, sem AFK, para pontuar. */
    public static final double THRESHOLD = 0.60;
    /** Bônus de quem está em Crag 10 min antes. */
    public static final Duration PRE_BONUS_BEFORE = Duration.ofMinutes(10);
    public static final long PRE_BONUS_POINTS = 2;
    /** Local mais velho que isso não conta para o bônus (a DLL envia a cada ~1 min). */
    public static final Duration FRESH_LOCATION = Duration.ofMinutes(3);
    /** Objetivos (criados na aba Objetivos) dados na CW. */
    public static final String OBJECTIVE_CW = "CW", OBJECTIVE_CW_MAJOR = "CW Major";
    /** PB Major: a cada 4 dias a partir de 27/09/2026 (Brasília); nesse dia as 3 CWs valem "CW Major". */
    public static final LocalDate PB_MAJOR_ANCHOR = LocalDate.of(2026, 9, 27);
    public static final int PB_MAJOR_EVERY_DAYS = 4;
    public static final String END_MESSAGE = "Due to the result of Chip War";
    /** Shouts no jogo antes do bônus de 10 min antes (minutos antes da CW → minutos até os pontos). */
    public static final Map<Integer, Integer> RG_SHOUTS = Map.of(15, 5, 12, 2);
    static String rgShout(int minutesToPoints) {
        return "RG CW - Pontos de RG serão distribuidos automaticamente em " + minutesToPoints + " minutos.";
    }
    /**
     * No máximo isso de pontos de CW por membro por dia, e o mesmo para o bônus de 10 min antes. As 3 CWs de um
     * dia do servidor (00h, 08h e 16h em Brasília) caem no mesmo dia de Brasília, então conta por cw_date.
     */
    public static final int PER_DAY = 2;

    /**
     * Janelas máximas das CWs no horário do servidor (Moscou), já com o "palco 1.1". O fim real vem da DLL;
     * sem aviso, a CW fica em aberto até o fim da janela e aí precisa de um Admin.
     */
    public record Window(int slot, String label, int startHour, int endHour) {}
    public static final List<Window> WINDOWS = List.of(
            new Window(1, "CW1", 6, 8),
            new Window(2, "CW2", 14, 17),
            new Window(3, "CW3", 22, 24));
    private static final ZoneId SERVER_ZONE = GameEventDefinitions.SERVER_ZONE;
    private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final ObjectiveRepository objectives;
    private final EventClaimRepository claims;
    private final PointsService pointsService;
    private final AuditLogService audit;
    private final WebSocketPublisher webSocketPublisher;
    private final CharacterStatusRepository statusRepository;
    private final ShoutService shoutService;
    /** Shouts já dados ("data|cw|minutos"): uma vez cada, mesmo com o tick a cada 30 s. */
    private final Set<String> shouted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }
    public Instant now() { return clock.instant(); }

    public record Slot(LocalDate date, Window window) {
        public Instant startsAt() { return date.atTime(window.startHour(), 0).atZone(SERVER_ZONE).toInstant(); }
        public Instant windowEndsAt() { return date.atStartOfDay(SERVER_ZONE).plusHours(window.endHour()).toInstant(); }
        public static Slot of(LocalDate date, int slot) { return new Slot(date, WINDOWS.get(slot - 1)); }
        /** Dia de Brasília do início: PB Major vale para as 3 CWs desse dia. */
        public boolean major() {
            long days = ChronoUnit.DAYS.between(PB_MAJOR_ANCHOR, startsAt().atZone(BRT).toLocalDate());
            return Math.floorMod(days, PB_MAJOR_EVERY_DAYS) == 0;
        }
    }

    /** A CW em andamento nesse instante (entre o início e o fim da janela); vazio fora das janelas. */
    public static Optional<Slot> slotAt(Instant instant) {
        return slotsAround(instant).stream()
                .filter(s -> !instant.isBefore(s.startsAt()) && instant.isBefore(s.windowEndsAt()))
                .findFirst();
    }

    /** CW que começa daqui a no máximo {@code before} (o instante está entre o início − before e o início). */
    public static Optional<Slot> startingWithin(Instant instant, Duration before) {
        Instant limit = instant.plus(before);
        return slotsAround(limit).stream()
                .filter(s -> s.startsAt().isAfter(instant) && !s.startsAt().isAfter(limit))
                .findFirst();
    }

    /** CW cuja faixa de amostras (5 min antes do início até o fim da janela) contém o instante. */
    static Optional<Slot> sampleSlotAt(Instant instant) {
        return slotsAround(instant).stream()
                .filter(s -> !instant.isBefore(s.startsAt().minus(SAMPLE_LEAD)) && instant.isBefore(s.windowEndsAt()))
                .findFirst();
    }

    private static List<Slot> slotsAround(Instant instant) {
        LocalDate day = instant.atZone(SERVER_ZONE).toLocalDate();
        var out = new ArrayList<Slot>();
        for (LocalDate d : List.of(day.minusDays(1), day, day.plusDays(1)))
            for (Window w : WINDOWS) out.add(new Slot(d, w));
        return out;
    }

    // ---- Amostras (envio de status da DLL) ----

    /**
     * Um envio de status de um membro. {@code idleSince} = desde quando está parado sem EXP (null = sem
     * coordenada, não dá para saber: nunca AFK).
     */
    public void record(long memberId, String character, String map, Instant idleSince, Instant now) {
        record(memberId, character, map, idleSince, null, null, null, null, now);
    }

    /**
     * Com coordenada, FP e a última vez que ganhou EXP: para pegar quem fica indo e voltando entre os mesmos pontos
     * sem usar skill ({@link #withPatrol}).
     */
    @Transactional
    public void record(long memberId, String character, String map, Instant idleSince,
                       Integer x, Integer y, Integer fp, Instant expGainedAt, Instant now) {
        Optional<Slot> slot = sampleSlotAt(now);
        if (slot.isEmpty()) return;
        boolean inMap = map != null && CW_MAP.equalsIgnoreCase(map.strip());
        jdbc.update("""
                INSERT INTO cw_sample(member_id,at,cw_date,cw_slot,in_map,idle_since,character_name,map_name,map_x,map_y,fp,exp_gained_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (member_id,at) DO NOTHING""",
                memberId, Timestamp.from(now), Date.valueOf(slot.get().date()), slot.get().window().slot(), inMap,
                idleSince == null ? null : Timestamp.from(idleSince), character, mapName(map), x, y, fp,
                expGainedAt == null ? null : Timestamp.from(expGainedAt));
    }

    private static String mapName(String map) {
        if (map == null || map.isBlank()) return null;
        String m = map.strip();
        return m.length() > 80 ? m.substring(0, 80) : m;
    }

    record Sample(Instant at, boolean inMap, Instant idleSince, String character, String map,
                  Integer x, Integer y, Integer fp, Instant expGainedAt) {
        Sample(Instant at, boolean inMap, Instant idleSince, String character, String map) {
            this(at, inMap, idleSince, character, map, null, null, null, null);
        }
        boolean afk() { return idleSince != null && Duration.between(idleSince, at).compareTo(AFK_AFTER) > 0; }
        boolean hasPoint() { return inMap && x != null && y != null; }
        Sample idleFrom(Instant since) {
            Instant idle = idleSince == null || since.isBefore(idleSince) ? since : idleSince;
            return new Sample(at, inMap, idle, character, map, x, y, fp, expGainedAt);
        }
    }

    /** Bot de CW: indo e voltando por pelo menos isso entre os mesmos pontos, sem skill nem EXP = AFK. */
    public static final Duration PATROL_WINDOW = Duration.ofMinutes(5);
    /** No máximo tantos pontos diferentes nesse tempo (quem anda de verdade passa por um ponto novo a cada envio). */
    public static final int PATROL_MAX_POINTS = 5;
    /** Coordenadas a até isso uma da outra (unidades do site = mundo / 10) são o mesmo ponto. */
    public static final int SAME_POINT = 2;

    /**
     * Marca como AFK os trechos de "patrulha": por {@link #PATROL_WINDOW} seguidos em Crag, andou entre 2 e
     * {@link #PATROL_MAX_POINTS} pontos e voltou a um ponto onde já tinha estado (A → B → A), sem o FP cair (não
     * usou skill) e sem ganhar EXP. SP não conta: correr também gasta SP. Parado num ponto só (RG) não é patrulha.
     * O trecho inteiro, desde o começo da janela, vira AFK (como o parado).
     */
    static List<Sample> withPatrol(List<Sample> samples) {
        int n = samples.size();
        boolean[] patrol = new boolean[n];
        for (int j = 0; j < n; ++j) {
            int i = j;
            while (i + 1 < n && samples.get(i).hasPoint() && near(samples.get(i), samples.get(i + 1))
                    && Duration.between(samples.get(j).at(), samples.get(i).at()).compareTo(PATROL_WINDOW) < 0) ++i;
            if (Duration.between(samples.get(j).at(), samples.get(i).at()).compareTo(PATROL_WINDOW) < 0) continue;
            if (patrolling(samples.subList(j, i + 1))) for (int k = j; k <= i; ++k) patrol[k] = true;
        }
        var out = new ArrayList<Sample>(n);
        Instant start = null;
        for (int k = 0; k < n; ++k) {
            Sample s = samples.get(k);
            if (!patrol[k]) { start = null; out.add(s); continue; }
            if (start == null || !patrol[k - 1] || !near(samples.get(k - 1), s)) start = s.at();
            out.add(s.idleFrom(start));
        }
        return out;
    }

    private static boolean patrolling(List<Sample> window) {
        Instant from = window.get(0).at();
        var points = new ArrayList<int[]>();
        var path = new ArrayList<Integer>();   // ponto de cada envio, sem repetir o anterior
        for (int k = 0; k < window.size(); ++k) {
            Sample s = window.get(k);
            if (!s.hasPoint()) return false;
            if (s.expGainedAt() != null && s.expGainedAt().isAfter(from)) return false;   // ganhou EXP
            if (k > 0) {
                Sample prev = window.get(k - 1);
                if (s.fp() == null || prev.fp() == null || s.fp() < prev.fp()) return false;   // usou skill (ou sem FP)
            }
            int id = -1;
            for (int p = 0; p < points.size() && id < 0; ++p)
                if (Math.abs(points.get(p)[0] - s.x()) <= SAME_POINT && Math.abs(points.get(p)[1] - s.y()) <= SAME_POINT) id = p;
            if (id < 0) { points.add(new int[]{s.x(), s.y()}); id = points.size() - 1; }
            if (path.isEmpty() || path.get(path.size() - 1) != id) path.add(id);
        }
        if (points.size() < 2 || points.size() > PATROL_MAX_POINTS) return false;
        return path.size() > new HashSet<>(path).size();   // voltou a um ponto onde já tinha estado
    }

    /**
     * Presença do membro: calculada por personagem, fica a do personagem com mais tempo ativo. Com dois personagens
     * online (um em Crag, outro fora) as amostras alternariam dentro/fora do mapa e nenhum intervalo contaria.
     * Retorna ativo, AFK, fora (em outro mapa) e o personagem.
     */
    record Best(long active, long afk, long outside, String character) {}
    static Best best(List<Sample> samples, Instant from, Instant to) {
        var byCharacter = new LinkedHashMap<String, List<Sample>>();
        for (Sample s : samples) byCharacter.computeIfAbsent(Objects.toString(s.character(), ""), k -> new ArrayList<>()).add(s);
        Best best = null;
        for (var e : byCharacter.entrySet()) {
            long[] p = presence(withPatrol(e.getValue()), from, to);
            if (best == null || p[0] > best.active() || (p[0] == best.active() && p[1] < best.afk()))
                best = new Best(p[0], p[1], p[2], e.getKey().isEmpty() ? null : e.getKey());
        }
        return best == null ? new Best(0, 0, 0, null) : best;
    }

    /**
     * Segundos [em Crag sem AFK, AFK, fora (em outro mapa)] entre {@code from} e {@code to}, de uma lista de
     * amostras em ordem. Saída de Crag (morreu e nasceu em outro mapa, TP) e volta: até {@link #OUTSIDE_GRACE}
     * fora não perde nada; mais que isso, o tempo fora inteiro vai para "fora".
     */
    static long[] presence(List<Sample> samples, Instant from, Instant to) {
        long[] out = {0, 0, 0};   // [ativo, AFK, fora]
        Instant afkFrom = null;   // começo do trecho parado de uma amostra AFK mais adiante
        for (int i = samples.size() - 1; i >= 1; --i) {
            Sample b = samples.get(i), a = samples.get(i - 1);
            if (b.afk()) afkFrom = b.idleSince();
            else if (afkFrom != null && !afkFrom.equals(b.idleSince())) afkFrom = null;
            if (!a.inMap() || !b.inMap() || !near(a, b)) continue;
            Instant lo = max(a.at(), from), hi = min(b.at(), to);
            if (!hi.isAfter(lo)) continue;
            Instant cut = afkFrom == null ? hi : max(lo, min(hi, afkFrom));   // [lo,cut) presente, [cut,hi) AFK
            add(out, 0, lo, cut, from, to);
            add(out, 1, cut, hi, from, to);
        }
        for (int i = 0; i + 1 < samples.size(); ++i) {
            Sample a = samples.get(i), left = samples.get(i + 1);
            if (a.inMap() && left.inMap() || !near(a, left)) continue;
            if (!a.inMap()) {                     // em outro mapa (ainda não tinha chegado em Crag, ou chegando)
                add(out, 2, a.at(), left.at(), from, to);
                continue;
            }
            // Saiu de Crag: a = último envio em Crag, left = primeiro fora.
            int j = i + 1;
            while (j + 1 < samples.size() && !samples.get(j + 1).inMap() && near(samples.get(j), samples.get(j + 1))) ++j;
            Sample lastOut = samples.get(j);
            Sample back = j + 1 < samples.size() && near(lastOut, samples.get(j + 1)) ? samples.get(j + 1) : null;
            // A CW acabou com ele ainda fora, dentro do prazo (a DLL seguia enviando): também não perde.
            boolean endedOutside = left.at().isBefore(to)
                    && !Duration.between(left.at(), to).minus(OUTSIDE_GRACE).isPositive()
                    && (back != null || !Duration.between(lastOut.at(), to).minus(MAX_GAP).isPositive());
            boolean grace = endedOutside
                    || (back != null && !Duration.between(left.at(), back.at()).minus(OUTSIDE_GRACE).isPositive());
            Instant end = back != null ? back.at() : grace ? to : lastOut.at();
            add(out, a.afk() ? 1 : 0, a.at(), left.at(), from, to);   // ainda em Crag até sair
            add(out, grace ? 0 : 2, left.at(), end, from, to);
            i = j;
        }
        return out;
    }

    /** Dois envios seguidos sem buraco (DLL fechada, caiu). */
    private static boolean near(Sample a, Sample b) {
        Duration gap = Duration.between(a.at(), b.at());
        return gap.isPositive() && gap.compareTo(MAX_GAP) <= 0;
    }

    /** Soma em {@code out[idx]} os segundos de [lo,hi) dentro de [from,to). */
    private static void add(long[] out, int idx, Instant lo, Instant hi, Instant from, Instant to) {
        lo = max(lo, from);
        hi = min(hi, to);
        if (hi.isAfter(lo)) out[idx] += Duration.between(lo, hi).getSeconds();
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    // ---- Fim da CW ----

    /**
     * A DLL de um membro viu a mensagem de fim da CW no chat. Só vale durante a CW, depois de 5 min do início;
     * o primeiro aviso fecha (os outros são ignorados). Devolve true se fechou agora.
     */
    @Transactional
    public boolean gameEnded(String character, String body) {
        if (body == null || !body.contains(END_MESSAGE)) throw new AppException("Mensagem inválida", HttpStatus.BAD_REQUEST);
        Member member = eligibleByCharacter(character)
                .orElseThrow(() -> new AppException("Personagem desconhecido", HttpStatus.NOT_FOUND));
        Instant now = now();
        Optional<Slot> slot = slotAt(now);
        if (slot.isEmpty() || now.isBefore(slot.get().startsAt().plus(MIN_LENGTH))) return false;
        return end(slot.get(), now, "GAME", member.getId());
    }

    /** Administrador+: marca o fim de uma CW que a DLL não avisou (ou corrige), antes de pontuar. */
    @Transactional
    public Overview adminEnd(LocalDate date, int number, Instant endedAt, int days) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        if (number < 1 || number > 3) throw new AppException("CW inválida", HttpStatus.BAD_REQUEST);
        Slot slot = Slot.of(date, number);
        if (endedAt.isBefore(slot.startsAt().plus(MIN_LENGTH)) || endedAt.isAfter(slot.windowEndsAt()) || endedAt.isAfter(now()))
            throw new AppException("O fim precisa estar entre 5 min depois do início e o fim da janela da CW.", HttpStatus.BAD_REQUEST);
        Integer awarded = jdbc.queryForObject("SELECT count(*) FROM cw_session WHERE cw_date=? AND cw_slot=? AND awarded_at IS NOT NULL",
                Integer.class, Date.valueOf(date), number);
        if (awarded != null && awarded > 0) throw new AppException("Essa CW já foi pontuada.", HttpStatus.CONFLICT);
        jdbc.update("""
                INSERT INTO cw_session(cw_date,cw_slot,ended_at,end_source,ended_by) VALUES(?,?,?,'ADMIN',?)
                ON CONFLICT (cw_date,cw_slot) DO UPDATE SET ended_at=EXCLUDED.ended_at, end_source='ADMIN', ended_by=EXCLUDED.ended_by""",
                Date.valueOf(date), number, Timestamp.from(endedAt), SecurityUtils.currentMember().getId());
        return overview(days);
    }

    private boolean end(Slot slot, Instant at, String source, Long by) {
        return jdbc.update("""
                INSERT INTO cw_session(cw_date,cw_slot,ended_at,end_source,ended_by) VALUES(?,?,?,?,?)
                ON CONFLICT (cw_date,cw_slot) DO UPDATE SET ended_at=EXCLUDED.ended_at, end_source=EXCLUDED.end_source,
                  ended_by=EXCLUDED.ended_by
                WHERE cw_session.ended_at IS NULL""",
                Date.valueOf(slot.date()), slot.window().slot(), Timestamp.from(at), source, by) > 0;
    }

    // ---- Agendador: bônus antes, fechamento e pontuação ----

    /** Chamado a cada 30 s (CwScheduler). Cada passo é idempotente. */
    @Transactional
    public void tick() {
        Instant now = now();
        for (Slot slot : slotsAround(now)) {
            for (var shout : RG_SHOUTS.entrySet()) {
                Instant at = slot.startsAt().minus(Duration.ofMinutes(shout.getKey()));
                if (!now.isBefore(at) && now.isBefore(at.plus(Duration.ofMinutes(1)))
                        && shouted.add(slot.date() + "|" + slot.window().slot() + "|" + shout.getKey())) {
                    shoutService.system(rgShout(shout.getValue()));
                }
            }
            shouted.removeIf(k -> k.compareTo(slot.date().minusDays(2).toString()) < 0);
            Instant bonusAt = slot.startsAt().minus(PRE_BONUS_BEFORE);
            if (!now.isBefore(bonusAt) && now.isBefore(bonusAt.plus(Duration.ofMinutes(3)))) preBonus(slot, now);
            if (!now.isBefore(slot.windowEndsAt()) && now.isBefore(slot.windowEndsAt().plus(Duration.ofHours(1))))
                jdbc.update("""
                        INSERT INTO cw_session(cw_date,cw_slot,closed_at) VALUES(?,?,?)
                        ON CONFLICT (cw_date,cw_slot) DO UPDATE SET closed_at=EXCLUDED.closed_at
                        WHERE cw_session.ended_at IS NULL AND cw_session.closed_at IS NULL""",
                        Date.valueOf(slot.date()), slot.window().slot(), Timestamp.from(now));
        }
        List<Slot> due = jdbc.query("SELECT cw_date,cw_slot FROM cw_session WHERE ended_at IS NOT NULL AND awarded_at IS NULL AND ended_at<?",
                (r, n) -> Slot.of(r.getDate(1).toLocalDate(), r.getInt(2)), Timestamp.from(now.minus(AWARD_DELAY)));
        for (Slot slot : due) award(slot, now);
    }

    /** 2 pontos para quem está em Crag 10 min antes da CW (AFK ou não). */
    private void preBonus(Slot slot, Instant now) {
        Date date = Date.valueOf(slot.date());
        int number = slot.window().slot();
        jdbc.update("INSERT INTO cw_session(cw_date,cw_slot) VALUES(?,?) ON CONFLICT DO NOTHING", date, number);
        if (jdbc.update("UPDATE cw_session SET pre_awarded_at=? WHERE cw_date=? AND cw_slot=? AND pre_awarded_at IS NULL",
                Timestamp.from(now), date, number) == 0) return;
        var inMap = new TreeSet<Long>();
        for (CharacterStatus s : statusRepository.findByLocationAtAfter(now.minus(FRESH_LOCATION)))
            if (CW_MAP.equalsIgnoreCase(Objects.toString(s.getMapName(), "").strip())) inMap.add(s.getMember().getId());
        String reason = slot.window().label() + " " + brtDay(slot) + ": em " + CW_MAP + " 10 min antes";
        for (Long id : inMap) {
            Member member = members.findByIdForUpdate(id).orElse(null);
            if (member == null || !eligible(member)) continue;
            if (awardsToday(date, id, "PRE") >= PER_DAY) continue;
            pointsService.adjustPoints(member, member, PRE_BONUS_POINTS, PointsModality.EVENTO, reason);
            jdbc.update("INSERT INTO cw_award(cw_date,cw_slot,member_id,kind,points) VALUES(?,?,?,'PRE',?) ON CONFLICT DO NOTHING",
                    date, number, id, PRE_BONUS_POINTS);
            audit.log(AuditLogType.POINTS_ADJUSTED, member, member, Map.of(
                    "amount", PRE_BONUS_POINTS, "modality", PointsModality.EVENTO.name(), "reason", reason, "source", "CW"));
            publishPoints(id);
        }
    }

    /** Pontua a CW: objetivo "CW" ou "CW Major" para quem ficou 60% do tempo em Crag sem AFK. */
    private void award(Slot slot, Instant now) {
        Date date = Date.valueOf(slot.date());
        int number = slot.window().slot();
        Instant endedAt = jdbc.queryForObject("SELECT ended_at FROM cw_session WHERE cw_date=? AND cw_slot=? AND awarded_at IS NULL FOR UPDATE",
                (r, n) -> r.getTimestamp(1).toInstant(), date, number);
        Objective objective = objective(slot.major() ? OBJECTIVE_CW_MAJOR : OBJECTIVE_CW).orElse(null);
        jdbc.update("UPDATE cw_session SET awarded_at=?, objective_id=? WHERE cw_date=? AND cw_slot=?",
                Timestamp.from(now), objective == null ? null : objective.getId(), date, number);
        if (objective == null) return;   // objetivo não existe: nada a dar (a tela avisa)
        long length = Duration.between(slot.startsAt(), endedAt).getSeconds();
        if (length <= 0) return;
        String reason = slot.window().label() + " " + brtDay(slot);
        for (var entry : new TreeMap<>(samplesByMember(slot)).entrySet()) {
            Best p = best(entry.getValue(), slot.startsAt(), endedAt);
            int percent = (int) Math.min(100, p.active() * 100 / length);
            if (p.active() < THRESHOLD * length) continue;
            Member member = members.findByIdForUpdate(entry.getKey()).orElse(null);
            if (member == null || !eligible(member)) continue;
            if (awardsToday(date, member.getId(), "CW") >= PER_DAY) continue;
            if (objective.getType() == ObjectiveType.LIMITADO
                    && claims.countDailyClaims(member.getId(), objective.getId()) >= objective.getDailyLimit()) continue;
            pointsService.adjustPoints(member, member, objective.getPoints(), PointsModality.EVENTO, reason);
            var claim = new EventClaim();
            claim.setMember(member);
            claim.setObjective(objective);
            claim.setPoints(objective.getPoints());
            claim = claims.save(claim);
            jdbc.update("INSERT INTO cw_award(cw_date,cw_slot,member_id,kind,points,percent,claim_id) VALUES(?,?,?,'CW',?,?,?) ON CONFLICT DO NOTHING",
                    date, number, member.getId(), objective.getPoints(), percent, claim.getId());
            var details = new LinkedHashMap<String, Object>();
            details.put("claimId", claim.getId());
            details.put("objectiveName", objective.getName());
            details.put("points", objective.getPoints());
            details.put("source", "CW");
            details.put("percent", percent);
            audit.log(AuditLogType.EVENT_CLAIMED, member, member, details);
            publishPoints(member.getId());
        }
    }

    /** Quantos pontos desse tipo (CW ou PRE) o membro já recebeu nas CWs do dia. */
    private int awardsToday(Date date, long memberId, String kind) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM cw_award WHERE cw_date=? AND member_id=? AND kind=?",
                Integer.class, date, memberId, kind);
        return n == null ? 0 : n;
    }

    private void publishPoints(Long memberId) {
        webSocketPublisher.publishPointsUpdate(memberId, () -> Map.of(
                "memberId", memberId, "availablePoints", pointsService.getAvailablePoints(memberId)));
    }

    private Optional<Objective> objective(String name) {
        return objectives.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(o -> o.getName().strip().equalsIgnoreCase(name))
                .findFirst();
    }

    private Map<Long, List<Sample>> samplesByMember(Slot slot) {
        var out = new HashMap<Long, List<Sample>>();
        jdbc.query("""
                SELECT member_id,at,in_map,idle_since,character_name,map_name,map_x,map_y,fp,exp_gained_at
                FROM cw_sample WHERE cw_date=? AND cw_slot=? ORDER BY member_id,at""",
                rs -> {
                    out.computeIfAbsent(rs.getLong("member_id"), k -> new ArrayList<>()).add(new Sample(
                            rs.getTimestamp("at").toInstant(), rs.getBoolean("in_map"),
                            ts(rs.getTimestamp("idle_since")), rs.getString("character_name"), rs.getString("map_name"),
                            rs.getObject("map_x", Integer.class), rs.getObject("map_y", Integer.class),
                            rs.getObject("fp", Integer.class), ts(rs.getTimestamp("exp_gained_at"))));
                }, Date.valueOf(slot.date()), slot.window().slot());
        return out;
    }

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private Optional<Member> eligibleByCharacter(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream().filter(CwAttendanceService::eligible).findFirst();
    }

    private static String brtDay(Slot slot) {
        var d = slot.startsAt().atZone(BRT);
        return String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    // ---- Leitura (Administrador+) ----

    /** outsideSeconds = em outro mapa (fora os 2 min de tolerância); map = último mapa visto durante a CW. */
    public record CwMember(long memberId, String nickname, String character, long activeSeconds, long afkSeconds,
                           long outsideSeconds, String map, int percent, Instant firstSeenAt, Instant lastSeenAt,
                           boolean awarded, boolean preBonus) {}
    /**
     * Uma CW. endedAt = fim real (null = ainda não acabou ou ninguém avisou); closed = a janela passou sem aviso.
     * objectiveName/objectivePoints = o que vale (CW ou CW Major); objectiveMissing = o objetivo não existe.
     */
    public record Cw(LocalDate date, int slot, String label, Instant startsAt, Instant windowEndsAt, Instant endedAt,
                     String endSource, boolean closed, Instant awardedAt, boolean major, String objectiveName,
                     Long objectivePoints, boolean objectiveMissing, List<CwMember> members, List<String> preBonus) {}
    /** canManage = Administrador+ (pode marcar o fim de uma CW sem aviso). */
    public record Overview(int days, int thresholdPercent, long preBonusPoints, boolean canManage, List<Cw> cws) {}

    /** Todos os membros aprovados: as CWs dos últimos {@code days} dias (a mais nova primeiro). */
    @Transactional(readOnly = true)
    public Overview overview(int days) {
        var viewer = SecurityUtils.currentMember();
        if (viewer.getStatus() != MemberStatus.APROVADO) throw new AppException("Membro ainda não aprovado.", HttpStatus.FORBIDDEN);
        int span = Math.max(1, Math.min(days, 90));
        Instant now = now();
        LocalDate since = now.atZone(SERVER_ZONE).toLocalDate().minusDays(span - 1L);
        var keys = new TreeSet<List<Integer>>(Comparator.<List<Integer>>comparingInt(k -> k.get(0)).thenComparingInt(k -> k.get(1)).reversed());
        jdbc.query("SELECT DISTINCT cw_date,cw_slot FROM cw_sample WHERE cw_date>=? UNION SELECT cw_date,cw_slot FROM cw_session WHERE cw_date>=?",
                rs -> { keys.add(List.of((int) rs.getDate(1).toLocalDate().toEpochDay(), rs.getInt(2))); },
                Date.valueOf(since), Date.valueOf(since));
        var nicknames = new HashMap<Long, String>();
        var cws = new ArrayList<Cw>();
        for (var key : keys) {
            Slot slot = Slot.of(LocalDate.ofEpochDay(key.get(0)), key.get(1));
            if (now.isBefore(slot.startsAt().minus(PRE_BONUS_BEFORE))) continue;
            Date date = Date.valueOf(slot.date());
            var session = jdbc.query("SELECT ended_at,end_source,closed_at,awarded_at FROM cw_session WHERE cw_date=? AND cw_slot=?",
                    (r, n) -> new Object[]{ts(r.getTimestamp(1)), r.getString(2), ts(r.getTimestamp(3)), ts(r.getTimestamp(4))},
                    date, slot.window().slot()).stream().findFirst().orElse(new Object[4]);
            Instant endedAt = (Instant) session[0];
            var awards = new HashMap<String, Set<Long>>();
            jdbc.query("SELECT kind,member_id FROM cw_award WHERE cw_date=? AND cw_slot=?",
                    rs -> { awards.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getLong(2)); },
                    date, slot.window().slot());
            Instant until = endedAt != null ? endedAt : min(now, slot.windowEndsAt());
            long length = Math.max(1, Duration.between(slot.startsAt(), until).getSeconds());
            var rows = new ArrayList<CwMember>();
            samplesByMember(slot).forEach((id, samples) -> {
                // Só o que foi visto durante a CW: a DLL segue enviando até a janela fechar, mesmo depois do fim.
                var seen = samples.stream().filter(Sample::inMap)
                        .filter(s -> !s.at().isBefore(slot.startsAt()) && !s.at().isAfter(until)).toList();
                if (seen.isEmpty()) return;
                Best p = best(samples, slot.startsAt(), until);
                // Mapa: último envio durante a CW (até o fim), do personagem que contou.
                String map = samples.stream()
                        .filter(s -> !s.at().isAfter(until) && (p.character() == null || p.character().equals(s.character())))
                        .reduce((x, y) -> y).map(s -> s.inMap() ? CW_MAP : s.map()).orElse(null);
                String nick = nicknames.computeIfAbsent(id, k -> members.findById(k).map(Member::getNickname).orElse("?"));
                rows.add(new CwMember(id, nick, p.character() != null ? p.character() : samples.get(samples.size() - 1).character(),
                        p.active(), p.afk(), p.outside(), map, (int) Math.min(100, p.active() * 100 / length), seen.get(0).at(), seen.get(seen.size() - 1).at(),
                        awards.getOrDefault("CW", Set.of()).contains(id), awards.getOrDefault("PRE", Set.of()).contains(id)));
            });
            rows.sort(Comparator.comparingLong(CwMember::activeSeconds).reversed().thenComparing(CwMember::nickname, String.CASE_INSENSITIVE_ORDER));
            var pre = awards.getOrDefault("PRE", Set.of()).stream()
                    .map(id -> nicknames.computeIfAbsent(id, k -> members.findById(k).map(Member::getNickname).orElse("?")))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            String objectiveName = slot.major() ? OBJECTIVE_CW_MAJOR : OBJECTIVE_CW;
            Optional<Objective> objective = objective(objectiveName);
            cws.add(new Cw(slot.date(), slot.window().slot(), slot.window().label(), slot.startsAt(), slot.windowEndsAt(),
                    endedAt, (String) session[1], session[2] != null && endedAt == null, (Instant) session[3], slot.major(),
                    objectiveName, objective.map(Objective::getPoints).orElse(null), objective.isEmpty(), rows, pre));
        }
        return new Overview(span, (int) Math.round(THRESHOLD * 100), PRE_BONUS_POINTS,
                viewer.getRole().isAtLeast(Role.ADMINISTRADOR), cws);
    }

    private static Instant ts(Timestamp t) { return t == null ? null : t.toInstant(); }
}
