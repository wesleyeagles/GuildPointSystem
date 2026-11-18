package com.guild.app.quest.service;

import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.quest.dto.BdCompletionRequest;
import com.guild.app.quest.repository.BdCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BdDailyService {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final String HASH = "9bb2778096af9f107c1884ea1e3e010be381abfeff45d821b306f458079eddd0";
    private final MemberRepository members;
    private final BdCompletionRepository completions;
    public record Row(String character, Instant completedAt) {}
    public record History(LocalDate day, String timezone, String pendingCode, List<Row> members) {}

    static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.isProfileComplete()
                && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }
    private void requireCharacter(String name) {
        if (name == null || name.isBlank() || name.length() > 32 ||
                members.findByGameCharacter(name.strip()).stream().noneMatch(BdDailyService::eligible))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Personagem sem cadastro aprovado.");
    }
    // Display-only: segue a identificação por personagem da DLL; não concede pontos ou recompensas.
    @Transactional
    public void ingest(BdCompletionRequest event) {
        requireCharacter(event.character());
        Instant at;
        try { at = Instant.ofEpochSecond(event.observedAt()); }
        catch (DateTimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        if (!"Q070613".equals(event.code()) || !HASH.equals(event.definitionHash()) ||
                event.previousSlot() < 0 || event.previousSlot() > 39 ||
                event.completedSlot() != event.previousSlot() + 103 ||
                at.isBefore(Instant.parse("2026-11-17T00:00:00Z")) || at.isAfter(Instant.now().plusSeconds(300)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sinal de conclusão não validado.");
        completions.record(event, at.atZone(ZONE).toLocalDate(), at);
    }
    @Transactional(readOnly = true)
    public History forSite(LocalDate day) {
        Member actor = members.findById(SecurityUtils.currentMember().getId()).orElseThrow();
        if (!eligible(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return history(day);
    }
    @Transactional(readOnly = true)
    public String forGame(String character) {
        requireCharacter(character);
        History history = history(LocalDate.now(ZONE));
        StringBuilder text = new StringBuilder("BD1\t" + history.day() + "\n");
        for (Row row : history.members()) text.append(row.character().replaceAll("[\\t\\r\\n]", " "))
                .append('\t').append(row.completedAt() == null ? "-" : row.completedAt().atZone(ZONE).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")))
                .append('\n');
        return text.toString();
    }
    private History history(LocalDate day) {
        if (day == null) day = LocalDate.now(ZONE);
        Map<String, Instant> done = completions.forDay(day);
        Map<String, Row> rows = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Member member : members.findByStatusOrderByPointsDesc(MemberStatus.APROVADO)) {
            if (!eligible(member)) continue;
            String nick = member.getNickname();
            rows.putIfAbsent(nick, new Row(nick, done.get(nick.toLowerCase(Locale.ROOT))));
        }
        return new History(day, ZONE.getId(), "Q394782", List.copyOf(rows.values()));
    }
}
