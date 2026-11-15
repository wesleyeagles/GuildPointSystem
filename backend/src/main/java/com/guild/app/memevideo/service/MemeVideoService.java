package com.guild.app.memevideo.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.cw.service.CwAttendanceService;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vídeo meme: Admin/Líder manda um vídeo do YouTube para um membro; a DLL dele (1.45.0+) busca em
 * GET /api/meme-videos/game e abre o vídeo numa janela no meio do jogo. Não chega para quem desligou no
 * perfil, para quem está numa raid nem durante a CW (o vídeo espera até {@link #PENDING} e depois some).
 * Um envio por membro a cada {@link #COOLDOWN}.
 */
@Service
@RequiredArgsConstructor
public class MemeVideoService {
    public static final Duration PENDING = Duration.ofMinutes(10);
    public static final Duration COOLDOWN = Duration.ofMinutes(10);
    /** Sem vídeo enviado nas próximas: a DLL não precisa nem esperar a CW começar. */
    static final Duration CW_LEAD = Duration.ofMinutes(10);
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern START = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s?)?");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private Clock clock = Clock.systemUTC();

    /** Testes: relógio fixo. */
    public void useClock(Clock value) { clock = value; }

    public record Video(String youtubeId, int startSecond) {}
    public record Sent(long id, String nickname, String sentBy, String youtubeId, int startSecond, Instant createdAt,
                       Instant playedAt, boolean expired) {}
    /** Vazio para quem não é Admin/Líder. */
    public record View(boolean canSend, List<Sent> recent) {}
    public record NewVideo(Long memberId, String url) {}
    public record GameVideo(long id, String youtubeId, int startSecond) {}

    // ---------------------------------------------------------------- link

    /** Id e início (?t=) de um link do YouTube (watch, youtu.be, shorts, embed, live) ou do id puro. */
    public static Optional<Video> parse(String url) {
        if (url == null) return Optional.empty();
        String text = url.strip();
        if (ID.matcher(text).matches()) return Optional.of(new Video(text, 0));
        URI uri;
        try {
            uri = URI.create(text.contains("://") ? text : "https://" + text);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.startsWith("www.")) host = host.substring(4);
        if (host.startsWith("m.")) host = host.substring(2);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        Map<String, String> query = query(uri.getRawQuery());
        String id = null;
        if (host.equals("youtu.be")) id = path.replaceFirst("^/", "");
        else if (host.equals("youtube.com") || host.equals("music.youtube.com") || host.equals("youtube-nocookie.com")) {
            if (path.equals("/watch")) id = query.get("v");
            else {
                Matcher m = Pattern.compile("^/(?:shorts|embed|live|v)/([^/]+)").matcher(path);
                if (m.find()) id = m.group(1);
            }
        }
        if (id == null || !ID.matcher(id).matches()) return Optional.empty();
        String t = query.getOrDefault("t", query.getOrDefault("start", ""));
        return Optional.of(new Video(id, start(t)));
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) out.putIfAbsent(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }

    /** "90", "90s", "1m30s", "1h2m3s" -> segundos; inválido = 0. */
    static int start(String t) {
        if (t == null || t.isBlank()) return 0;
        Matcher m = START.matcher(t.strip());
        if (!m.matches()) return 0;
        long s = 0;
        if (m.group(1) != null) s += Long.parseLong(m.group(1)) * 3600;
        if (m.group(2) != null) s += Long.parseLong(m.group(2)) * 60;
        if (m.group(3) != null) s += Long.parseLong(m.group(3));
        return (int) Math.min(s, 24 * 3600);
    }

    // ---------------------------------------------------------------- site

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }

    private static boolean sender(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }

    private Member member(MemberPrincipal actor) {
        return members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public View view(MemberPrincipal actor) {
        if (!sender(member(actor))) return new View(false, List.of());
        return new View(true, recent());
    }

    private List<Sent> recent() {
        Instant expiredBefore = clock.instant().minus(PENDING);
        return jdbc.query("""
            SELECT v.*, m.nickname, s.nickname AS sender FROM meme_video v JOIN member m ON m.id=v.member_id
            LEFT JOIN member s ON s.id=v.sent_by ORDER BY v.created_at DESC, v.id DESC LIMIT 20""",
            (r, n) -> {
                Instant created = r.getTimestamp("created_at").toInstant();
                Timestamp played = r.getTimestamp("played_at");
                return new Sent(r.getLong("id"), r.getString("nickname"), r.getString("sender"), r.getString("youtube_id"),
                    r.getInt("start_second"), created, played == null ? null : played.toInstant(),
                    played == null && created.isBefore(expiredBefore));
            });
    }

    @Transactional
    public View send(NewVideo body, MemberPrincipal actor) {
        Member admin = member(actor);
        if (!sender(admin)) throw new AppException("Só Admin ou Líder manda vídeo.", HttpStatus.FORBIDDEN);
        if (body == null || body.memberId() == null) throw new AppException("Escolha o membro.", HttpStatus.BAD_REQUEST);
        Video video = parse(body.url()).orElseThrow(() -> new AppException("Link do YouTube inválido.", HttpStatus.BAD_REQUEST));
        Member target = members.findById(body.memberId()).filter(MemeVideoService::eligible)
            .orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (optedOut(target.getId())) throw new AppException(target.getNickname() + " não quer receber vídeos.", HttpStatus.CONFLICT);
        Timestamp since = Timestamp.from(clock.instant().minus(COOLDOWN));
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM meme_video WHERE member_id=? AND created_at>?)",
                Boolean.class, target.getId(), since)))
            throw new AppException(target.getNickname() + " já recebeu um vídeo há pouco. Espere 10 minutos.", HttpStatus.TOO_MANY_REQUESTS);
        jdbc.update("INSERT INTO meme_video(member_id,sent_by,youtube_id,start_second,created_at) VALUES(?,?,?,?,?)",
            target.getId(), admin.getId(), video.youtubeId(), video.startSecond(), Timestamp.from(clock.instant()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("video", "https://youtu.be/" + video.youtubeId());
        if (video.startSecond() > 0) payload.put("start", video.startSecond());
        audit.log(AuditLogType.MEME_VIDEO_SENT, admin, target, payload);
        return new View(true, recent());
    }

    private boolean optedOut(long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT meme_video_opt_out FROM member WHERE id=?", Boolean.class, memberId));
    }

    @Transactional(readOnly = true)
    public boolean optOut(MemberPrincipal actor) { return optedOut(actor.getId()); }

    @Transactional
    public boolean setOptOut(MemberPrincipal actor, boolean value) {
        jdbc.update("UPDATE member SET meme_video_opt_out=? WHERE id=?", value, actor.getId());
        // Desligou: o que estava esperando não abre mais.
        if (value) jdbc.update("DELETE FROM meme_video WHERE member_id=? AND played_at IS NULL", actor.getId());
        return value;
    }

    // ---------------------------------------------------------------- jogo

    /** O vídeo mais antigo esperando por este personagem, se dá para abrir agora. */
    @Transactional(readOnly = true)
    public Optional<GameVideo> next(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        Instant now = clock.instant();
        Timestamp since = Timestamp.from(now.minus(PENDING));
        // A DLL de todo mundo consulta a cada 5 s: sem vídeo esperando, nem procura o membro.
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM meme_video WHERE played_at IS NULL AND created_at>?)",
                Boolean.class, since)))
            return Optional.empty();
        Optional<Member> member = members.findByGameCharacter(character.strip()).stream().filter(MemeVideoService::eligible).findFirst();
        if (member.isEmpty() || optedOut(member.get().getId()) || busy(member.get().getId(), now)) return Optional.empty();
        return jdbc.query("""
            SELECT id,youtube_id,start_second FROM meme_video WHERE member_id=? AND played_at IS NULL AND created_at>?
            ORDER BY created_at, id LIMIT 1""",
            (r, n) -> new GameVideo(r.getLong(1), r.getString(2), r.getInt(3)), member.get().getId(), since).stream().findFirst();
    }

    /** Em raid ou com CW em andamento (ou começando): o vídeo espera. */
    public boolean busy(long memberId, Instant now) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM raid_member WHERE member_id=?)", Boolean.class, memberId)))
            return true;
        if (CwAttendanceService.startingWithin(now, CW_LEAD).isPresent()) return true;
        return CwAttendanceService.slotAt(now).map(slot -> !Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM cw_session WHERE cw_date=? AND cw_slot=? AND (ended_at IS NOT NULL OR closed_at IS NOT NULL))""",
            Boolean.class, Date.valueOf(slot.date()), slot.window().slot()))).orElse(false);
    }

    /** A DLL abriu o vídeo: não volta mais. */
    @Transactional
    public boolean played(long id, String character) {
        if (character == null || character.isBlank() || character.length() > 32) return false;
        Timestamp now = Timestamp.from(clock.instant());
        for (Member m : members.findByGameCharacter(character.strip()))
            if (jdbc.update("UPDATE meme_video SET played_at=? WHERE id=? AND played_at IS NULL AND member_id=?", now, id, m.getId()) == 1)
                return true;
        return false;
    }
}
