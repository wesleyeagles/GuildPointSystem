package com.guild.app.translate.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Tradutor do chat do jogo: a DLL de quem está na lista lê as linhas em russo (e, desde a 1.40.0, em indonésio)
 * do chat, manda para cá e mostra a tradução em inglês no próprio chat do jogo (só na tela dela). Como os outros envios da DLL,
 * vai pelo nome do personagem (sem login); só responde a personagem de membro aprovado na lista.
 * Para gastar cada vez menos a cota do MyMemory, cada mensagem passa antes pelo dicionário:
 *  - normalizada (ChatText.normalize) vira a chave; o que a API já traduziu fica salvo em chat_translation;
 *  - termos do glossário (chat_glossary, editado pela liderança) são trocados antes de traduzir, e mensagem
 *    só com termos do glossário nem vai para a API;
 *  - o glossário também aprende sozinho (V116): mensagem de uma palavra só traduzida pela API vira termo
 *    "aprendido", e frase em que todas as palavras já são conhecidas (manuais ou aprendidas) é montada sem a API.
 *    Frase com palavra desconhecida vai inteira para a API (traduz melhor que palavra por palavra).
 * Um cache em memória na frente evita ir ao banco nas repetições mais comuns.
 */
@Service
@RequiredArgsConstructor
public class ChatTranslateService {
    public static final int MAX_LINES = 10, MAX_LINE_LENGTH = 400, LINES_PER_MINUTE = 60, CACHE_SIZE = 5000;
    public static final int MAX_GLOSSARY = 2000, MAX_TERM_LENGTH = 100, MAX_TRANSLATION_LENGTH = 1000;
    public static final int MAX_LEARNED = 10000, MAX_LEARNED_WORDS = 3;

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private final ChatTranslator translator;
    private final Map<Long, Deque<Instant>> recent = new HashMap<>();
    private final Map<String, String> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > CACHE_SIZE; }
    });
    private volatile ChatText.Glossary glossary;   // só os termos salvos à mão
    private volatile ChatText.Glossary vocabulary; // manuais + aprendidos
    Clock clock = Clock.systemUTC();

    public record Translator(Long id, String nickname) {}
    public record GlossaryTerm(Long id, String source, String translation, boolean learned) {}
    /**
     * Uso de hoje (dia de Brasília). apiChars é o que conta na cota do MyMemory (dailyLimit; 0 = desconhecido);
     * quotaPausedUntil = cota acabou e o site parou de chamar a API até essa hora.
     */
    public record Usage(LocalDate day, long apiChars, long dailyLimit, long apiCalls, long savedHits, long glossaryHits,
                        long failed, Instant quotaPausedUntil, String warning) {}
    /** Só vem preenchido para quem pode configurar (Administrador+); savedTranslations = frases no dicionário. */
    public record AccessView(boolean canConfigure, List<Translator> translators, List<GlossaryTerm> glossary,
                             long savedTranslations, Usage usage) {}

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private enum Count { API, SAVED, GLOSSARY, FAILED }

    private static boolean eligible(Member m) {
        return m.getStatus() == MemberStatus.APROVADO && m.isProfileComplete()
            && m.getDeletedAt() == null && m.getSuspendedAt() == null;
    }
    private static boolean configurer(Member m) { return eligible(m) && m.getRole().isAtLeast(Role.ADMINISTRADOR); }
    private boolean listed(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chat_translator WHERE member_id=?)", Boolean.class, memberId));
    }

    /** Membro aprovado da lista que usa este personagem. */
    @Transactional(readOnly = true)
    public Optional<Member> user(String character) {
        if (character == null || character.isBlank() || character.length() > 32) return Optional.empty();
        return members.findByGameCharacter(character.strip()).stream()
            .filter(ChatTranslateService::eligible).filter(m -> listed(m.getId())).findFirst();
    }

    /**
     * Uma linha por mensagem (UTF-8) e a tradução na mesma posição; linha vazia = não traduziu.
     * 403 fora da lista, 400 pedido grande demais, 429 linhas demais no último minuto.
     */
    public String translate(String character, String body) {
        Member member = user(character).orElseThrow(() ->
            new AppException("Tradutor do chat não liberado para este personagem.", HttpStatus.FORBIDDEN));
        List<String> lines = body == null ? List.of() : List.of(body.replace("\r", "").split("\n"));
        if (lines.isEmpty() || lines.size() > MAX_LINES)
            throw new AppException("Envie de 1 a " + MAX_LINES + " linhas.", HttpStatus.BAD_REQUEST);
        reserve(member.getId(), lines.size());
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            String text = line.strip();
            String key = ChatText.normalize(text);
            String result = text.isEmpty() || text.length() > MAX_LINE_LENGTH ? null : lookup(key);
            out.add(result == null || result.equalsIgnoreCase(text) || result.equalsIgnoreCase(key) ? "" : result);
        }
        return String.join("\n", out) + "\n";
    }

    /** Tradução da mensagem normalizada: memória, glossário, dicionário salvo e, por último, a API. */
    private String lookup(String key) {
        boolean russian = ChatText.hasCyrillic(key);
        if (!russian && !ChatText.looksIndonesian(key)) return null;
        String cached = cache.get(key);
        if (cached != null) {
            count(Count.SAVED, 0);
            return cached;
        }
        String replaced = russian ? glossary().apply(key) : key;  // o glossário é só de termos em russo
        String result;
        if (russian && !ChatText.hasCyrillic(replaced)) {
            result = replaced;  // só termos do glossário: não chama a API
            count(Count.GLOSSARY, 0);
        } else {
            result = jdbc.query("UPDATE chat_translation SET hits=hits+1, used_at=now() WHERE source=? RETURNING translation",
                (r, n) -> r.getString(1), key).stream().findFirst().orElse(null);
            String composed = result == null ? vocabulary().applyComplete(key) : null;
            if (result != null) {
                count(Count.SAVED, 0);
            } else if (composed != null) {
                result = composed;  // todas as palavras já conhecidas: não chama a API
                count(Count.GLOSSARY, 0);
            } else {
                result = (russian ? translator.translate(replaced) : translator.translate(replaced, "id|en")).map(t -> t.replaceAll("[\\r\\n\\t]+", " ").strip())
                    .filter(t -> !t.isEmpty()).orElse(null);
                if (result == null) {  // falhou ou sem cota: tenta de novo na próxima vez
                    count(Count.FAILED, 0);
                    return null;
                }
                count(Count.API, replaced.length());
                if (result.length() > MAX_TRANSLATION_LENGTH) result = result.substring(0, MAX_TRANSLATION_LENGTH);
                jdbc.update("INSERT INTO chat_translation(source,translation) VALUES(?,?) ON CONFLICT (source) DO NOTHING", key, result);
                learn(key, result);
            }
        }
        cache.put(key, result);
        return result;
    }

    private LocalDate today() { return LocalDate.ofInstant(clock.instant(), BRASILIA); }

    private void count(Count what, int chars) {
        jdbc.update("""
            INSERT INTO chat_translate_usage(day,api_chars,api_calls,saved_hits,glossary_hits,failed) VALUES(?,?,?,?,?,?)
            ON CONFLICT (day) DO UPDATE SET
                api_chars=chat_translate_usage.api_chars+EXCLUDED.api_chars,
                api_calls=chat_translate_usage.api_calls+EXCLUDED.api_calls,
                saved_hits=chat_translate_usage.saved_hits+EXCLUDED.saved_hits,
                glossary_hits=chat_translate_usage.glossary_hits+EXCLUDED.glossary_hits,
                failed=chat_translate_usage.failed+EXCLUDED.failed""",
            today(), chars, what == Count.API ? 1 : 0, what == Count.SAVED ? 1 : 0,
            what == Count.GLOSSARY ? 1 : 0, what == Count.FAILED ? 1 : 0);
    }

    private Usage usage() {
        LocalDate day = today();
        Instant paused = translator.quotaPausedUntil().orElse(null);
        String warning = translator.warning().orElse(null);
        long limit = translator.dailyLimit();
        return jdbc.query("SELECT api_chars,api_calls,saved_hits,glossary_hits,failed FROM chat_translate_usage WHERE day=?",
                (r, n) -> new Usage(day, r.getLong(1), limit, r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5), paused, warning),
                day).stream().findFirst()
            .orElse(new Usage(day, 0, limit, 0, 0, 0, 0, paused, warning));
    }

    private ChatText.Glossary glossary() {
        ChatText.Glossary g = glossary;
        if (g == null) glossary = g = loadGlossary(false);
        return g;
    }

    private ChatText.Glossary vocabulary() {
        ChatText.Glossary g = vocabulary;
        if (g == null) vocabulary = g = loadGlossary(true);
        return g;
    }

    private ChatText.Glossary loadGlossary(boolean withLearned) {
        Map<String, String> terms = new HashMap<>();
        jdbc.query("SELECT source,translation FROM chat_glossary" + (withLearned ? "" : " WHERE NOT learned"),
            r -> { terms.put(r.getString(1), r.getString(2)); });
        return ChatText.Glossary.of(terms);
    }

    private static final java.util.regex.Pattern ONE_WORD = java.util.regex.Pattern.compile("\\p{L}+");

    /**
     * Mensagem de uma palavra só que a API traduziu: a palavra entra no glossário como aprendida (nunca troca
     * termo que já existe). Tradução de até 3 palavras, sem pontuação no fim; "Hello" vira "hello", "WTS" fica.
     */
    public static String learnable(String key, String translation) {
        if (!ONE_WORD.matcher(key).matches() || key.length() > MAX_TERM_LENGTH) return null;
        String t = translation.replaceAll("[.!?,;:]+$", "").strip();
        if (t.isEmpty() || t.length() > MAX_TERM_LENGTH || t.equalsIgnoreCase(key)) return null;
        if (t.split("\\s+").length > MAX_LEARNED_WORDS) return null;
        if (!t.equals(t.toUpperCase(Locale.ROOT))) t = t.substring(0, 1).toLowerCase(Locale.ROOT) + t.substring(1);
        return t;
    }

    private void learn(String key, String translation) {
        String word = learnable(key, translation);
        if (word == null) return;
        Long learned = jdbc.queryForObject("SELECT count(*) FROM chat_glossary WHERE learned", Long.class);
        if (learned != null && learned >= MAX_LEARNED) return;
        if (jdbc.update("INSERT INTO chat_glossary(source,translation,learned) VALUES(?,?,true) ON CONFLICT (source) DO NOTHING",
                key, word) > 0)
            vocabulary = null;
    }

    /** Esquece o que está em memória (glossário e traduções); o banco continua valendo. */
    public void forgetCaches() {
        glossary = null;
        vocabulary = null;
        cache.clear();
    }

    private synchronized void reserve(Long memberId, int lines) {
        Instant now = clock.instant(), cutoff = now.minusSeconds(60);
        Deque<Instant> window = recent.computeIfAbsent(memberId, id -> new ArrayDeque<>());
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) window.pollFirst();
        if (window.size() + lines > LINES_PER_MINUTE)
            throw new AppException("Muitas linhas para traduzir; aguarde um pouco.", HttpStatus.TOO_MANY_REQUESTS);
        for (int i = 0; i < lines; i++) window.addLast(now);
    }

    @Transactional(readOnly = true)
    public AccessView view(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        return configurer(member) ? full() : new AccessView(false, List.of(), List.of(), 0, null);
    }

    private AccessView full() {
        Long saved = jdbc.queryForObject("SELECT count(*) FROM chat_translation", Long.class);
        return new AccessView(true, translators(), glossaryTerms(), saved == null ? 0 : saved, usage());
    }

    private List<Translator> translators() {
        return jdbc.query("SELECT m.id,m.nickname FROM chat_translator t JOIN member m ON m.id=t.member_id ORDER BY lower(m.nickname)",
            (r, n) -> new Translator(r.getLong("id"), r.getString("nickname")));
    }

    private List<GlossaryTerm> glossaryTerms() {
        return jdbc.query("SELECT id,source,translation,learned FROM chat_glossary ORDER BY source",
            (r, n) -> new GlossaryTerm(r.getLong("id"), r.getString("source"), r.getString("translation"), r.getBoolean("learned")));
    }

    private Member configurerOrThrow(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!configurer(member))
            throw new AppException("Apenas Administradores e o Líder mexem no tradutor do chat.", HttpStatus.FORBIDDEN);
        return member;
    }

    /** Administrador+: substitui a lista inteira de quem usa o tradutor. */
    @Transactional
    public AccessView update(List<Long> memberIds, MemberPrincipal actor) {
        Member member = configurerOrThrow(actor);
        var ids = new LinkedHashSet<>(memberIds == null ? List.<Long>of() : memberIds);
        if (ids.size() > 500) throw new AppException("Lista grande demais.", HttpStatus.BAD_REQUEST);
        for (Long id : ids)
            if (!members.existsById(id)) throw new AppException("Membro não encontrado", HttpStatus.NOT_FOUND);
        jdbc.update("DELETE FROM chat_translator");
        for (Long id : ids) jdbc.update("INSERT INTO chat_translator(member_id,added_by) VALUES(?,?)", id, member.getId());
        audit.log(AuditLogType.CHAT_TRANSLATE_ACCESS_UPDATED, member, member,
            Map.of("translators", translators().stream().map(Translator::nickname).toList()));
        return full();
    }

    /** Administrador+: cria ou troca um termo do glossário (o termo em russo é normalizado). */
    @Transactional
    public AccessView saveTerm(String source, String translation, MemberPrincipal actor) {
        Member member = configurerOrThrow(actor);
        String term = ChatText.normalize(source);
        String english = translation == null ? "" : translation.replaceAll("\\s+", " ").strip();
        if (term.isEmpty() || term.length() > MAX_TERM_LENGTH || !ChatText.hasCyrillic(term))
            throw new AppException("Informe o termo em russo (até " + MAX_TERM_LENGTH + " letras).", HttpStatus.BAD_REQUEST);
        if (english.isEmpty() || english.length() > MAX_TERM_LENGTH)
            throw new AppException("Informe a tradução (até " + MAX_TERM_LENGTH + " letras).", HttpStatus.BAD_REQUEST);
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM chat_glossary WHERE source=? AND NOT learned)", Boolean.class, term));
        Long count = jdbc.queryForObject("SELECT count(*) FROM chat_glossary WHERE NOT learned", Long.class);
        if (!exists && count != null && count >= MAX_GLOSSARY)
            throw new AppException("O glossário chegou ao limite de " + MAX_GLOSSARY + " termos.", HttpStatus.BAD_REQUEST);
        jdbc.update("""
            INSERT INTO chat_glossary(source,translation,updated_by) VALUES(?,?,?)
            ON CONFLICT (source) DO UPDATE SET translation=EXCLUDED.translation, updated_by=EXCLUDED.updated_by,
                updated_at=now(), learned=false""",
            term, english, member.getId());
        termChanged(term);
        audit.log(AuditLogType.CHAT_TRANSLATE_GLOSSARY_UPDATED, member, member, Map.of("source", term, "translation", english));
        return full();
    }

    /** Administrador+: tira um termo do glossário. */
    @Transactional
    public AccessView deleteTerm(Long id, MemberPrincipal actor) {
        Member member = configurerOrThrow(actor);
        String term = jdbc.query("DELETE FROM chat_glossary WHERE id=? RETURNING source", (r, n) -> r.getString(1), id)
            .stream().findFirst().orElseThrow(() -> new AppException("Termo não encontrado", HttpStatus.NOT_FOUND));
        termChanged(term);
        audit.log(AuditLogType.CHAT_TRANSLATE_GLOSSARY_UPDATED, member, member, Map.of("source", term, "removed", true));
        return full();
    }

    /** Traduções salvas que tinham o termo são refeitas na próxima vez que aparecerem. */
    private void termChanged(String term) {
        jdbc.update("DELETE FROM chat_translation WHERE strpos(source, ?) > 0", term);
        forgetCaches();
        // De novo depois do commit: uma tradução no meio do caminho pode ter lido o glossário antigo.
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { forgetCaches(); }
            });
    }
}
