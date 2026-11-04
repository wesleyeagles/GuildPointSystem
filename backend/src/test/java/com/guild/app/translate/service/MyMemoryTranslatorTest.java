package com.guild.app.translate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guild.app.translate.service.MyMemoryTranslator.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Respostas reais do MyMemory (04/11/2026) e e-mails como o painel de deploy pode guardar. */
class MyMemoryTranslatorTest {
    private final ObjectMapper json = new ObjectMapper();

    private MyMemoryTranslator.Answer parse(String body) throws Exception {
        return MyMemoryTranslator.parse(json.readTree(body), 200);
    }

    @Test void cleansEmailFromDeployPanels() {
        assertEquals("a.b@gmail.com", MyMemoryTranslator.cleanEmail("  a.b@gmail.com "));
        assertEquals("a.b@gmail.com", MyMemoryTranslator.cleanEmail("\"a.b@gmail.com\""));
        assertEquals("a.b@gmail.com", MyMemoryTranslator.cleanEmail("'a.b@gmail.com'"));
        assertEquals("a.b@gmail.com", MyMemoryTranslator.cleanEmail("Wesley <a.b@gmail.com>"));
        assertEquals("", MyMemoryTranslator.cleanEmail(null));
        assertEquals("", MyMemoryTranslator.cleanEmail("  "));
    }

    @Test void readsMyMemoryAnswers() throws Exception {
        var ok = parse("{\"responseData\":{\"translatedText\":\"blue dog\",\"match\":0.85},\"quotaFinished\":false,\"responseStatus\":200}");
        assertEquals(Outcome.OK, ok.outcome());
        assertEquals("blue dog", ok.text());

        var invalid = parse("{\"responseData\":{\"translatedText\":\"INVALID EMAIL PROVIDED\"},\"quotaFinished\":null,"
            + "\"responseDetails\":\"INVALID EMAIL PROVIDED\",\"responseStatus\":\"403\"}");
        assertEquals(Outcome.INVALID_EMAIL, invalid.outcome());

        var quota = parse("{\"responseData\":{\"translatedText\":\"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY.\"},"
            + "\"quotaFinished\":true,\"responseStatus\":429}");
        assertEquals(Outcome.QUOTA, quota.outcome());

        assertEquals(Outcome.ERROR, parse("{\"responseData\":{\"translatedText\":\"\"},\"responseStatus\":500}").outcome());
    }
}
