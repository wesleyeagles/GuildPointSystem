package com.guild.app.translate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MYMEMORY_EMAIL tem que chegar ao tradutor pelo application.yml (guild.translate.mymemory-email).
 * Antes o código lia app.translate.*, que não existe no yml, e o e-mail nunca era usado.
 */
@SpringBootTest(classes = {MyMemoryTranslator.class, ObjectMapper.class}, properties = "MYMEMORY_EMAIL=guilda@example.com")
class MyMemoryEmailConfigTest {
    @Autowired MyMemoryTranslator translator;

    @Test void emailFromEnvironmentReachesTheTranslator() {
        assertEquals(MyMemoryTranslator.EMAIL_LIMIT, translator.dailyLimit());
        assertTrue(translator.warning().isEmpty());
    }
}
