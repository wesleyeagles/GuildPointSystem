package com.guild.app.pbstatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Le https://cerberus-games.com/kbase/pb/ sem login. A kbase guarda o servidor escolhido em cookie
 * (/kbase/server?srv=1 = FORCE&STEEL, o nosso): escolhe uma vez antes da primeira leitura. Depois de uma falha
 * espera 30 s antes de tentar de novo.
 */
@Slf4j
@Component
public class CerberusPbStatusClient implements PbStatusSource {
    static final Duration ERROR_PAUSE = Duration.ofSeconds(30);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private final String base, server;
    private final HttpClient http;
    private volatile boolean serverChosen;
    private volatile Instant pausedUntil = Instant.EPOCH;
    Clock clock = Clock.systemUTC();

    public CerberusPbStatusClient(@Value("${guild.market.url:https://cerberus-games.com}") String base,
                                  @Value("${guild.pb-status.server:1}") String server) {
        this.base = base.replaceAll("/+$", "");
        this.server = server;
        this.http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public synchronized Optional<String> page() {
        if (clock.instant().isBefore(pausedUntil)) return Optional.empty();
        try {
            if (!serverChosen) {
                get(base + "/kbase/server?srv=" + server);
                serverChosen = true;
            }
            return Optional.of(get(base + "/kbase/pb/"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            serverChosen = false;
            pausedUntil = clock.instant().plus(ERROR_PAUSE);
            log.warn("Status de PB da kbase pausado por {}: {}", ERROR_PAUSE, e.getMessage());
            return Optional.empty();
        }
    }

    private String get(String url) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode() + " em " + url);
        return response.body();
    }
}
