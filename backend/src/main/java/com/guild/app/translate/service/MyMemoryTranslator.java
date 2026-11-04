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
    static final long ANONYMOUS_LIMIT = 5_000, EMAIL_LIMIT = 50_000;
    static final String INVALID_EMAIL = "INVALID EMAIL PROVIDED";

    private final String email;
    /** O MyMemory recusou o e-mail: segue sem ele (cota anônima) e o painel avisa. */
    private volatile boolean emailRejected;
    private volatile Instant pausedUntil = Instant.EPOCH;
    private volatile Instant quotaPausedUntil = Instant.EPOCH;
    Clock clock = Clock.systemUTC();

    public MyMemoryTranslator(ObjectMapper json, @Value("${app.translate.mymemory-email:}") String email) {
        this.json = json;
        this.email = cleanEmail(email);
    }

    /** Tira espaços, aspas e "Nome <...>" que o painel de deploy às vezes guarda junto (o MyMemory recusa). */
    static String cleanEmail(String raw) {
        if (raw == null) return "";
        String e = raw.strip();
        int open = e.lastIndexOf('<'), close = e.lastIndexOf('>');
        if (open >= 0 && close > open) e = e.substring(open + 1, close);
        return e.replaceAll("[\"'\\s]", "");
    }

    /** Resposta do MyMemory: texto traduzido, ou o motivo de não ter vindo. */
    enum Outcome { OK, QUOTA, INVALID_EMAIL, ERROR }
    record Answer(Outcome outcome, String text, int status) {}

    static Answer parse(JsonNode body, int httpStatus) {
        int status = body.path("responseStatus").asInt(httpStatus);
        String translated = body.path("responseData").path("translatedText").asText("").strip();
        if (translated.startsWith(INVALID_EMAIL) || body.path("responseDetails").asText("").startsWith(INVALID_EMAIL))
            return new Answer(Outcome.INVALID_EMAIL, null, status);
        if (status == 429 || body.path("quotaFinished").asBoolean(false) || translated.startsWith("MYMEMORY WARNING"))
            return new Answer(Outcome.QUOTA, null, status);
        if (status != 200 || translated.isEmpty()) return new Answer(Outcome.ERROR, null, status);
        return new Answer(Outcome.OK, translated, status);
    }

    @Override
    public Optional<String> translate(String text) {
        if (clock.instant().isBefore(pausedUntil)) return Optional.empty();
        try {
            Answer answer = call(text, emailRejected ? "" : email);
            if (answer.outcome() == Outcome.INVALID_EMAIL && !email.isEmpty() && !emailRejected) {
                emailRejected = true;
                log.warn("MyMemory recusou o e-mail de MYMEMORY_EMAIL; traduzindo sem ele (cota anônima)");
                answer = call(text, "");
            }
            switch (answer.outcome()) {
                case OK -> { return Optional.of(answer.text()); }
                case QUOTA -> {
                    pause(QUOTA_PAUSE, "cota do MyMemory esgotada");
                    quotaPausedUntil = pausedUntil;
                }
                default -> pause(ERROR_PAUSE, "MyMemory respondeu " + answer.status());
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            pause(ERROR_PAUSE, "falha ao chamar o MyMemory: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Answer call(String text, String contact) throws Exception {
        String url = URL + "?langpair=ru%7Cen&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
            + (contact.isEmpty() ? "" : "&de=" + URLEncoder.encode(contact, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parse(json.readTree(response.body()), response.statusCode());
    }

    @Override
    public long dailyLimit() { return email.isEmpty() || emailRejected ? ANONYMOUS_LIMIT : EMAIL_LIMIT; }

    @Override
    public Optional<String> warning() {
        if (emailRejected) return Optional.of("O MyMemory recusou o e-mail de MYMEMORY_EMAIL; usando a cota anônima (~5 mil).");
        if (email.isEmpty()) return Optional.of("MYMEMORY_EMAIL não está definido no servidor; usando a cota anônima (~5 mil).");
        return Optional.empty();
    }

    @Override
    public Optional<Instant> quotaPausedUntil() {
        Instant until = quotaPausedUntil;
        return clock.instant().isBefore(until) ? Optional.of(until) : Optional.empty();
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("Tradutor do chat pausado por {}: {}", duration, reason);
    }
}
