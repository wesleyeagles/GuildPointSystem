package com.guild.app.shout.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shout do Líder/Administrador, enviado pela DLL de dentro do jogo: o texto aparece grande no jogo
 * de todos os membros com a DLL. Como os outros envios da DLL, o envio é ligado pelo nome do
 * personagem (sem login), aceito só se o personagem é de um Líder/Administrador aprovado.
 * Só o último shout fica guardado (em memória) por {@link #VISIBLE_SECONDS}; a DLL consulta a cada
 * poucos segundos e mostra cada id uma vez só. Sem shout ativo a consulta nem toca no banco.
 */
@Service
@RequiredArgsConstructor
public class ShoutService {
    public static final int MAX_LENGTH = 120;
    static final int VISIBLE_SECONDS = 60;
    static final int COOLDOWN_SECONDS = 10;

    private final MemberRepository members;
    private final AuditLogService audit;
    private final AtomicReference<Shout> current = new AtomicReference<>();
    Clock clock = Clock.systemUTC();

    public record Shout(long id, String author, String message, Instant createdAt) {}

    /** Conta Líder/Administrador aprovada que usa este personagem (o Líder primeiro). */
    public Optional<Member> sender(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream()
            .filter(ShoutService::eligible)
            .filter(m -> m.getRole().isAtLeast(Role.ADMINISTRADOR))
            .max(Comparator.comparing(Member::getRole));
    }

    public synchronized Shout send(String character, String text) {
        Member actor = sender(character).orElseThrow(() ->
            new AppException("Só o Líder e os Administradores podem dar shout.", HttpStatus.FORBIDDEN));
        String message = clean(text);
        if (message.isEmpty()) throw new AppException("Digite a mensagem do shout.", HttpStatus.BAD_REQUEST);
        if (message.length() > MAX_LENGTH)
            throw new AppException("O shout pode ter no máximo " + MAX_LENGTH + " caracteres.", HttpStatus.BAD_REQUEST);
        Instant now = clock.instant();
        Shout previous = current.get();
        if (previous != null && now.isBefore(previous.createdAt().plusSeconds(COOLDOWN_SECONDS)))
            throw new AppException("Aguarde alguns segundos antes de outro shout.", HttpStatus.CONFLICT);
        // Ids crescentes mesmo após reiniciar o backend: a DLL compara com o último que mostrou.
        long id = Math.max(previous == null ? 0 : previous.id() + 1, now.toEpochMilli());
        Shout shout = new Shout(id, actor.getNickname(), message, now);
        current.set(shout);
        audit.log(AuditLogType.SHOUT, actor, null, Map.of("message", message, "character", character.strip()));
        return shout;
    }

    /** Shout ativo para a DLL do personagem; vazio se não há shout ou o personagem não é de membro aprovado. */
    public Optional<Shout> forGame(String character) {
        Shout shout = current.get();
        if (shout == null || !clock.instant().isBefore(shout.createdAt().plusSeconds(VISIBLE_SECONDS)))
            return Optional.empty();
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        boolean member = members.findByGameCharacter(character.strip()).stream().anyMatch(ShoutService::eligible);
        return member ? Optional.of(shout) : Optional.empty();
    }

    private static boolean eligible(Member member) {
        return member.getStatus() == MemberStatus.APROVADO && member.isProfileComplete()
            && member.getDeletedAt() == null && member.getSuspendedAt() == null;
    }

    /** Uma linha só: a resposta da DLL é separada por TAB e quebra de linha. */
    static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll(" {2,}", " ").trim();
    }
}
