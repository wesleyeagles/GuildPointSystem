package com.guild.app.cw.service;

import com.guild.app.common.enums.Role;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.schedule.GameEventDefinitions;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Presença em CW: quanto tempo cada membro ficou no mapa da CW (Crag Mind Field) durante cada CW, pelo envio de
 * status da DLL (a cada ~1 min, com mapa e coordenada). O tempo entre dois envios seguidos no mapa conta como
 * presença; se o personagem estava AFK (parado na mesma coordenada há mais de 3 min sem ganhar EXP, a mesma regra
 * dos Pontos de RG), conta à parte, como AFK. Só histórico (não dá pontos). Leitura só de Administrador+.
 */
@Service
@RequiredArgsConstructor
public class CwAttendanceService {

    /** Nome do mapa como a DLL envia. */
    public static final String CW_MAP = "Crag Mind Field";
    /** Parado (sem EXP) por mais que isso = AFK, como no "Pontos de RG". */
    public static final Duration AFK_AFTER = Duration.ofMinutes(3);
    /** Envios mais distantes que isso (DLL fechou, caiu, carregando): o intervalo não conta. */
    public static final Duration MAX_GAP = Duration.ofSeconds(150);

    /**
     * Janelas das CWs no horário do servidor (Moscou), já com o "palco 1.1" (a CW pode durar até o fim dele):
     * CW1 06h–08h, CW2 14h–17h, CW3 22h–00h.
     */
    public record Window(int slot, String label, int startHour, int endHour) {}
    public static final List<Window> WINDOWS = List.of(
            new Window(1, "CW1", 6, 8),
            new Window(2, "CW2", 14, 17),
            new Window(3, "CW3", 22, 24));
    private static final ZoneId SERVER_ZONE = GameEventDefinitions.SERVER_ZONE;

    private final JdbcTemplate jdbc;
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo (para "hoje" na leitura). */
    public void useClock(Clock value) { clock = value; }

    public record Slot(LocalDate date, Window window) {
        public Instant startsAt() { return date.atTime(window.startHour(), 0).atZone(SERVER_ZONE).toInstant(); }
        public Instant endsAt() { return date.atStartOfDay(SERVER_ZONE).plusHours(window.endHour()).toInstant(); }
    }

    /** A CW em andamento nesse instante; vazio fora das janelas. */
    public static Optional<Slot> slotAt(Instant instant) {
        ZonedDateTime local = instant.atZone(SERVER_ZONE);
        int hour = local.getHour();
        return WINDOWS.stream()
                .filter(w -> hour >= w.startHour() && hour < w.endHour())
                .findFirst()
                .map(w -> new Slot(local.toLocalDate(), w));
    }

    /**
     * Um envio de status de um membro. {@code idleSince} = desde quando está parado sem EXP (null = sem
     * coordenada, não dá para saber: nunca AFK).
     */
    @Transactional
    public void record(long memberId, String character, String map, Instant idleSince, Instant now) {
        Optional<Slot> slot = slotAt(now);
        if (slot.isEmpty()) return;
        Date date = Date.valueOf(slot.get().date());
        int number = slot.get().window().slot();
        Timestamp at = Timestamp.from(now);
        if (map == null || !CW_MAP.equalsIgnoreCase(map.strip())) {
            // Fora do mapa da CW: só marca, para o próximo envio no mapa não contar o tempo fora.
            jdbc.update("UPDATE cw_attendance SET last_tick_at=GREATEST(last_tick_at,?), in_map=false "
                    + "WHERE cw_date=? AND cw_slot=? AND member_id=?", at, date, number, memberId);
            return;
        }
        boolean afk = idleSince != null && Duration.between(idleSince, now).compareTo(AFK_AFTER) > 0;
        String gap = "CASE WHEN cw_attendance.in_map AND EXCLUDED.last_tick_at > cw_attendance.last_tick_at "
                + "AND EXCLUDED.last_tick_at - cw_attendance.last_tick_at <= make_interval(secs => " + MAX_GAP.toSeconds() + ") "
                + "THEN CAST(EXTRACT(EPOCH FROM EXCLUDED.last_tick_at - cw_attendance.last_tick_at) AS INT) ELSE 0 END";
        jdbc.update("""
                INSERT INTO cw_attendance(cw_date,cw_slot,member_id,character_name,first_seen_at,last_seen_at,last_tick_at,in_map)
                VALUES(?,?,?,?,?,?,?,true)
                ON CONFLICT (cw_date,cw_slot,member_id) DO UPDATE SET
                  active_seconds = cw_attendance.active_seconds + CASE WHEN ? THEN 0 ELSE %1$s END,
                  afk_seconds = cw_attendance.afk_seconds + CASE WHEN ? THEN %1$s ELSE 0 END,
                  character_name = EXCLUDED.character_name,
                  last_seen_at = GREATEST(cw_attendance.last_seen_at, EXCLUDED.last_seen_at),
                  last_tick_at = GREATEST(cw_attendance.last_tick_at, EXCLUDED.last_tick_at),
                  in_map = true
                """.formatted(gap), date, number, memberId, character, at, at, at, afk, afk);
    }

    public record CwMember(long memberId, String nickname, String character, int activeSeconds, int afkSeconds,
                           Instant firstSeenAt, Instant lastSeenAt) {}
    /** Uma CW (dia + horário) com quem a DLL viu no mapa, mais tempo presente primeiro. */
    public record Cw(LocalDate date, int slot, String label, Instant startsAt, Instant endsAt, List<CwMember> members) {}
    public record Overview(int days, List<Cw> cws) {}

    /** Administrador+: as CWs dos últimos {@code days} dias (a mais nova primeiro). */
    @Transactional(readOnly = true)
    public Overview overview(int days) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        int span = Math.max(1, Math.min(days, 90));
        LocalDate since = clock.instant().atZone(SERVER_ZONE).toLocalDate().minusDays(span - 1L);
        var byCw = new LinkedHashMap<List<Object>, List<CwMember>>();
        jdbc.query("""
                SELECT a.cw_date,a.cw_slot,m.id,m.nickname,a.character_name,a.active_seconds,a.afk_seconds,
                       a.first_seen_at,a.last_seen_at
                FROM cw_attendance a JOIN member m ON m.id=a.member_id
                WHERE a.cw_date>=?
                ORDER BY a.cw_date DESC, a.cw_slot DESC, a.active_seconds DESC, lower(m.nickname)
                """, rs -> {
            var key = List.<Object>of(rs.getDate("cw_date").toLocalDate(), rs.getInt("cw_slot"));
            byCw.computeIfAbsent(key, k -> new ArrayList<>()).add(new CwMember(
                    rs.getLong("id"), rs.getString("nickname"), rs.getString("character_name"),
                    rs.getInt("active_seconds"), rs.getInt("afk_seconds"),
                    rs.getTimestamp("first_seen_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant()));
        }, Date.valueOf(since));
        var cws = byCw.entrySet().stream().map(e -> {
            LocalDate date = (LocalDate) e.getKey().get(0);
            int number = (Integer) e.getKey().get(1);
            Window window = WINDOWS.get(number - 1);
            Slot slot = new Slot(date, window);
            return new Cw(date, number, window.label(), slot.startsAt(), slot.endsAt(), e.getValue());
        }).toList();
        return new Overview(span, cws);
    }
}
