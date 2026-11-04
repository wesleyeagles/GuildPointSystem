package com.guild.app.translate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * MyMemory (api.mymemory.translated.net), plano gratuito sem chave: ~5 mil caracteres/dia por IP, ou ~50 mil
 * com um e-mail de contato (app.translate.mymemory-email). Quando a cota acaba ou o serviço falha, para de
 * chamar por um tempo e devolve vazio (a DLL simplesmente não mostra a tradução).
 */
@Slf4j
@Component
public class MyMemoryTranslator implements ChatTranslator {
    static final String URL = "https://api.mymemory.translated.net/get";
    static final Duration QUOTA_PAUSE = Duration.ofHours(1), ERROR_PAUSE = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String email;
    private volatile Instant pausedUntil = Instant.EPOCH;
    Clock clock = Clock.systemUTC();

    public MyMemoryTranslator(ObjectMapper json, @Value("${app.translate.mymemory-email:}") String email) {
        this.json = json;
        this.email = email == null ? "" : email.strip();
    }

    @Override
    public Optional<String> translate(String text) {
        if (clock.instant().isBefore(pausedUntil)) return Optional.empty();
        String url = URL + "?langpair=ru%7Cen&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
            + (email.isEmpty() ? "" : "&de=" + URLEncoder.encode(email, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode body = json.readTree(response.body());
            int status = body.path("responseStatus").asInt(response.statusCode());
            String translated = body.path("responseData").path("translatedText").asText("");
            if (status == 429 || status == 403 || translated.startsWith("MYMEMORY WARNING")) {
                pause(QUOTA_PAUSE, "cota do MyMemory esgotada");
                return Optional.empty();
            }
            if (status != 200 || translated.isBlank()) {
                pause(ERROR_PAUSE, "MyMemory respondeu " + status);
                return Optional.empty();
            }
            return Optional.of(translated.strip());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            pause(ERROR_PAUSE, "falha ao chamar o MyMemory: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("Tradutor do chat pausado por {}: {}", duration, reason);
    }
}
