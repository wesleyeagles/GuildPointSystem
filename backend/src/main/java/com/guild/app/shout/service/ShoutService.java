package com.guild.app.shout.service;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.translate.service.ChatTranslator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shout do Líder/Administrador, enviado pela DLL de dentro do jogo: o texto aparece grande no jogo
 * de todos os membros com a DLL. Como os outros envios da DLL, o envio é ligado pelo nome do
 * personagem (sem login), aceito só se o personagem é de um Líder/Administrador aprovado.
 * Só o último shout fica guardado (em memória) por {@link #VISIBLE_SECONDS}; a DLL consulta a cada
 * poucos segundos e mostra cada id uma vez só. Sem shout ativo a consulta nem toca no banco.
 * <p>
 * Membros com a tag "English Player" ({@link GameLanguage}) recebem o shout em inglês: os automáticos do site
 * já trazem o texto em inglês; o do Líder/Administrador é traduzido (PT -> EN, MyMemory) logo depois do envio.
 * Para esses jogadores o shout espera a tradução por até {@link #TRANSLATION_WAIT_SECONDS} (a DLL mostra cada id
 * uma vez só) e, se ela não vier, vai em português.
 */
@Service
@RequiredArgsConstructor
public class ShoutService {
    public static final int MAX_LENGTH = 120;
    static final int VISIBLE_SECONDS = 60;
    static final int COOLDOWN_SECONDS = 10;
    static final int TRANSLATION_WAIT_SECONDS = 8;

    private final MemberRepository members;
    private final AuditLogService audit;
    private final GameLanguage language;
    private final ChatTranslator translator;
    private final AtomicReference<Shout> current = new AtomicReference<>();
    private final ExecutorService translating = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shout-translate");
        t.setDaemon(true);
        return t;
    });
    Clock clock = Clock.systemUTC();

    /** {@code messageEn}: texto para os "English Players" (null = ainda sem tradução). */
    public record Shout(long id, String author, String message, String messageEn, Instant createdAt) {
        Shout withEnglish(String english) { return new Shout(id, author, message, english, createdAt); }
        /** O shout como um jogador de inglês vê. */
        Shout inEnglish() {
            return new Shout(id, SYSTEM_AUTHOR.equals(author) ? SYSTEM_AUTHOR_EN : author,
                messageEn == null ? message : messageEn, messageEn, createdAt);
        }
    }

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
        Shout shout = new Shout(id, actor.getNickname(), message, null, now);
        current.set(shout);
        audit.log(AuditLogType.SHOUT, actor, null, Map.of("message", message, "character", character.strip()));
        if (language.anyEnglish()) translating.execute(() -> translate(shout));
        return shout;
    }

    /** PT -> EN para os "English Players"; sem tradução fica o próprio texto (ex.: o Líder já escreveu em inglês). */
    void translate(Shout shout) {
        String english = translator.translate(shout.message(), "pt|en")
            .map(ShoutService::clean).filter(t -> !t.isEmpty()).orElse(shout.message());
        current.compareAndSet(shout, shout.withEnglish(english));
    }

    /** Autor dos shouts automáticos do site (sem personagem por trás). */
    public static final String SYSTEM_AUTHOR = "Sistema", SYSTEM_AUTHOR_EN = "System";

    /**
     * Shout automático do site (ex.: aviso de RG antes da CW): aparece no jogo de todos os membros com a DLL,
     * como o shout de um Líder, sem o intervalo mínimo entre shouts. {@code english}: o mesmo aviso para os
     * "English Players".
     */
    public synchronized Shout system(String text, String english) {
        String message = clean(text), messageEn = clean(english);
        if (message.isEmpty() || message.length() > MAX_LENGTH || messageEn.isEmpty() || messageEn.length() > MAX_LENGTH)
            throw new IllegalArgumentException("Shout do sistema inválido: " + text + " / " + english);
        Instant now = clock.instant();
        Shout previous = current.get();
        long id = Math.max(previous == null ? 0 : previous.id() + 1, now.toEpochMilli());
        Shout shout = new Shout(id, SYSTEM_AUTHOR, message, messageEn, now);
        current.set(shout);
        audit.log(AuditLogType.SHOUT, (Member) null, null, Map.of("message", message, "source", "SYSTEM"));
        return shout;
    }

    /**
     * Shout ativo para a DLL do personagem, já no idioma dele; vazio se não há shout ou o personagem não é de
     * membro aprovado.
     */
    public Optional<Shout> forGame(String character) {
        Shout shout = current.get();
        Instant now = clock.instant();
        if (shout == null || !now.isBefore(shout.createdAt().plusSeconds(VISIBLE_SECONDS)))
            return Optional.empty();
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        boolean english = language.isEnglish(character);
        // Ainda traduzindo: o jogador de inglês recebe numa próxima consulta (cada id aparece uma vez só).
        if (english && shout.messageEn() == null && now.isBefore(shout.createdAt().plusSeconds(TRANSLATION_WAIT_SECONDS)))
            return Optional.empty();
        boolean member = members.findByGameCharacter(character.strip()).stream().anyMatch(ShoutService::eligible);
        if (!member) return Optional.empty();
        return Optional.of(english ? shout.inEnglish() : shout);
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
