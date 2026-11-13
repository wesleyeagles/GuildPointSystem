package com.guild.app.translate;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.translate.service.ChatText;
import com.guild.app.translate.service.ChatTranslateService;
import com.guild.app.translate.service.ChatTranslator;
import com.guild.app.websocket.WebSocketPublisher;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatTranslateTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean WebSocketPublisher ws;
    @MockitoBean ChatTranslator translator;
    @Autowired ChatTranslateService service;
    @Autowired MemberRepository members;
    long allowedId;
    String allowedNick, otherNick, pendingNick;

    long member(String nick, String status) { return member(nick, status, "MEMBRO"); }
    long member(String nick, String status, String role) {
        jdbc.update("INSERT INTO member(nickname,role,status,profile_complete) VALUES(?,?,?,true)", nick, role, status);
        return jdbc.queryForObject("SELECT id FROM member WHERE nickname=?", Long.class, nick);
    }
    static String nick(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }

    ResultActions translate(String character, String body) throws Exception {
        var request = post("/api/chat-translate/game").contentType("text/plain;charset=UTF-8");
        if (character != null) request.param("character", character);
        return mvc.perform(request.content(body.getBytes(StandardCharsets.UTF_8)));
    }

    @BeforeEach void setup() {
        allowedNick = nick("Tra");
        allowedId = member(allowedNick, "APROVADO");
        otherNick = nick("Oth");
        member(otherNick, "APROVADO");
        pendingNick = nick("Pen");
        jdbc.update("INSERT INTO chat_translator(member_id) VALUES(?)", member(pendingNick, "PENDENTE"));
        jdbc.update("INSERT INTO chat_translator(member_id) VALUES(?)", allowedId);
    }

    @AfterEach void forget() { service.forgetCaches(); }

    String ok(String body) throws Exception {
        return translate(allowedNick, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    MemberPrincipal as(long id) { return new MemberPrincipal(members.findById(id).orElseThrow()); }

    @Test void normalizesText() {
        assertEquals("рес!", ChatText.normalize("  Рёс!!!   "));
        assertEquals("кто идет на босса?", ChatText.normalize("Кто   идёт на БОССА???"));
        var glossary = ChatText.Glossary.of(Map.of("хил", "heal", "инв", "invite", "кинь инв", "send invite"));
        assertEquals("нужен heal", glossary.apply("нужен хил"));
        assertEquals("хилер тут", glossary.apply("хилер тут"));               // só palavra inteira
        assertEquals("send invite плз", glossary.apply("кинь инв плз"));       // o termo mais longo primeiro
    }

    @Test void savedTranslationsSurviveRestartAndIgnoreCaseAndPunctuation() throws Exception {
        when(translator.translate("добрый вечер!")).thenReturn(Optional.of("good evening!"));
        assertEquals("good evening!\n", ok("Добрый вечер!!!"));
        service.forgetCaches();                                              // como um reinício do site
        assertEquals("good evening!\n", ok("добрый   ВЕЧЕР!"));
        verify(translator, times(1)).translate(anyString());
        assertEquals(1, jdbc.queryForObject("SELECT hits FROM chat_translation WHERE source='добрый вечер!'", Integer.class));
    }

    @Test void glossaryTermsSkipOrHelpTheApi() throws Exception {
        when(translator.translate("нужен heal")).thenReturn(Optional.of("need heal"));
        assertEquals("heal please\nneed heal\n", ok("хил плз\nНужен хил"));
        verify(translator, times(1)).translate(anyString());                // "хил плз" nem foi para a API
    }

    @Test void adminEditsGlossaryAndStaleTranslationsAreRedone() throws Exception {
        long admin = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        when(translator.translate("кинь кота")).thenReturn(Optional.of("throw the cat"));
        assertEquals("throw the cat\n", ok("кинь кота"));
        var view = service.saveTerm("  КОТА ", "cat buff", as(admin));
        assertTrue(view.glossary().stream().anyMatch(t -> t.source().equals("кота") && t.translation().equals("cat buff")));
        when(translator.translate("кинь cat buff")).thenReturn(Optional.of("give cat buff"));
        assertEquals("give cat buff\n", ok("кинь кота"));
        long id = view.glossary().stream().filter(t -> t.source().equals("кота")).findFirst().orElseThrow().id();
        service.deleteTerm(id, as(admin));
        assertEquals("throw the cat\n", ok("кинь кота"));
        MemberPrincipal adminUser = as(admin), commonUser = as(allowedId);
        assertThrows(AppException.class, () -> service.saveTerm("hello", "hi", adminUser));    // termo tem de ser russo
        assertThrows(AppException.class, () -> service.saveTerm("кот", "cat", commonUser));    // membro comum não edita
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE type='CHAT_TRANSLATE_GLOSSARY_UPDATED' AND actor_id=?", Integer.class, admin));
    }

    @Test void countsDailyUsageForTheQuota() throws Exception {
        long admin = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        var before = service.view(as(admin)).usage();
        when(translator.translate("бегом сюда")).thenReturn(Optional.of("come here"));
        when(translator.translate("бегом сюда!")).thenReturn(Optional.of("come here!"));
        when(translator.translate("тест провал")).thenReturn(Optional.empty());
        when(translator.dailyLimit()).thenReturn(50_000L);
        ok("бегом сюда\nбегом сюда\nхил плз\nтест провал");
        service.forgetCaches();
        ok("Бегом сюда!");                                                   // outra chave: vai para a API
        ok("бегом сюда");                                                    // do dicionário salvo
        var after = service.view(as(admin)).usage();
        assertEquals("бегом сюда".length() + "бегом сюда!".length(), after.apiChars() - before.apiChars());
        assertEquals(2, after.apiCalls() - before.apiCalls());
        assertEquals(2, after.savedHits() - before.savedHits());             // memória + banco
        assertEquals(1, after.glossaryHits() - before.glossaryHits());
        assertEquals(1, after.failed() - before.failed());
        assertEquals(50_000L, after.dailyLimit());
        assertNull(after.quotaPausedUntil());
    }

    @Test void onlyListedApprovedCharactersHaveAccess() throws Exception {
        mvc.perform(get("/api/chat-translate/game/access").param("character", allowedNick)).andExpect(status().isNoContent());
        mvc.perform(get("/api/chat-translate/game/access").param("character", otherNick)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat-translate/game/access").param("character", pendingNick)).andExpect(status().isNotFound());
        mvc.perform(get("/api/chat-translate/game/access")).andExpect(status().isNotFound());
        translate(otherNick, "привет").andExpect(status().isForbidden());
        translate(pendingNick, "привет").andExpect(status().isForbidden());
        verifyNoInteractions(translator);
    }

    @Test void translatesEachLineInOrderAndCachesRepeats() throws Exception {
        when(translator.translate("привет")).thenReturn(Optional.of("hello"));
        when(translator.translate("как дела")).thenReturn(Optional.of("how are\nyou"));
        when(translator.translate("ошибка")).thenReturn(Optional.empty());
        String body = translate(allowedNick, "привет\nкак дела\nошибка\n\nпривет")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("hello\nhow are you\n\n\nhello\n", body);
        verify(translator, times(1)).translate("привет");
    }

    @Test void untranslatedEchoIsDropped() throws Exception {
        when(translator.translate(anyString())).thenAnswer(a -> Optional.of(a.getArgument(0)));
        String body = translate(allowedNick, "кек").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("\n", body);
    }

    @Test void learnsSingleWordsAndBuildsSentencesFromThem() throws Exception {
        when(translator.translate("большая")).thenReturn(Optional.of("Big."));
        when(translator.translate("собака")).thenReturn(Optional.of("dog"));
        when(translator.translate("злая")).thenReturn(Optional.of("evil"));
        assertEquals("Big.\ndog\n", ok("Большая\nсобака"));
        assertEquals("big", jdbc.queryForObject("SELECT translation FROM chat_glossary WHERE source='большая' AND learned", String.class));
        // Todas as palavras conhecidas: monta sem a API (pontuação e números ficam).
        assertEquals("big dog!\n", ok("большая собака!"));
        verify(translator, never()).translate("большая собака!");
        // Palavra desconhecida: a frase inteira vai para a API.
        when(translator.translate("большая серая собака")).thenReturn(Optional.of("big gray dog"));
        assertEquals("big gray dog\n", ok("большая серая собака"));
        // Termo salvo à mão troca o aprendido e passa a valer na hora.
        long admin = member(nick("Adm"), "APROVADO", "ADMINISTRADOR");
        service.saveTerm("собака", "doggo", as(admin));
        assertFalse(jdbc.queryForObject("SELECT learned FROM chat_glossary WHERE source='собака'", Boolean.class));
        assertEquals("big doggo\n", ok("большая собака"));
        assertTrue(service.view(as(admin)).glossary().stream().anyMatch(t -> t.source().equals("большая") && t.learned()));

        assertEquals("hello", ChatTranslateService.learnable("привет", "Hello!"));
        assertEquals("WTS", ChatTranslateService.learnable("продам", "WTS"));
        assertEquals("good evening", ChatTranslateService.learnable("вечер", "Good evening."));
        assertNull(ChatTranslateService.learnable("добрый вечер", "good evening"));         // frase: não aprende
        assertNull(ChatTranslateService.learnable("слово1", "word"));
        assertNull(ChatTranslateService.learnable("кек", "one two three four"));
        assertNull(ChatTranslateService.learnable("кек", "кек"));
    }

    @Test void indonesianIsRecognizedByItsWords() {
        assertTrue(ChatText.looksIndonesian("gw jual keen talic murah"));
        assertTrue(ChatText.looksIndonesian("udah mau mulai belum?"));
        assertTrue(ChatText.looksIndonesian("kamu dimana bang"));
        assertTrue(ChatText.looksIndonesian("jual"));
        assertTrue(ChatText.looksIndonesian("ayo ke hq"));
        assertFalse(ChatText.looksIndonesian("wtb talsam@60k"));
        assertFalse(ChatText.looksIndonesian("hi dan"));
        assertFalse(ChatText.looksIndonesian("tp to hq now"));
        assertFalse(ChatText.looksIndonesian("vamos na boss agora"));
        assertFalse(ChatText.looksIndonesian("mau tempo hoje"));
        assertFalse(ChatText.looksIndonesian("não tem ninguem aqui ainda"));
        assertFalse(ChatText.looksIndonesian("i sell keen talic cheap, pm me"));
        assertFalse(ChatText.looksIndonesian("привет"));
        assertFalse(ChatText.looksIndonesian(""));
    }

    @Test void indonesianGoesToTheApiAsIdEn() throws Exception {
        when(translator.translate("gw jual keen talic murah", "id|en")).thenReturn(Optional.of("I sell keen talic cheap"));
        when(translator.translate("привет")).thenReturn(Optional.of("hello"));
        String body = translate(allowedNick, "Gw jual keen talic  murah\nWTS keen talic\nпривет\nvamos na boss")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("I sell keen talic cheap\n\nhello\n\n", body);
        translate(allowedNick, "gw jual keen talic murah").andExpect(status().isOk());   // repetida: do dicionário
        verify(translator, times(1)).translate("gw jual keen talic murah", "id|en");
        verify(translator, never()).translate(eq("wts keen talic"), anyString());
        verify(translator, never()).translate("wts keen talic");
    }

    @Test void limitsBatchSizeAndRate() throws Exception {
        when(translator.translate(anyString())).thenReturn(Optional.of("x"));
        translate(allowedNick, "a\n".repeat(11)).andExpect(status().isBadRequest());
        for (int i = 0; i < 6; i++) translate(allowedNick, ("слово" + i + "\n").repeat(10)).andExpect(status().isOk());
        translate(allowedNick, "ещё").andExpect(status().isTooManyRequests());
    }
}
