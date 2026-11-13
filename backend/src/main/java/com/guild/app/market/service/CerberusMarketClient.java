package com.guild.app.market.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lê o market da Cerberus com a conta da guild (guild.market.*). O market só abre logado: guarda os cookies
 * do XenForo e, se uma página voltar deslogada, entra de novo uma vez. Busca por código do item
 * (/market/?like=código&sort=priceup): essa busca traz TODOS os anúncios do item numa página só (a barra de
 * páginas só divide o total por 12, e page= é ignorado; visto em 11/11/2026). Se o site falhar, para de chamar
 * por um tempo e devolve vazio.
 */
@Slf4j
@Component
public class CerberusMarketClient implements MarketSource {
    static final Duration ERROR_PAUSE = Duration.ofSeconds(30), LOGIN_PAUSE = Duration.ofMinutes(5);
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private final String base, login, password;
    private final HttpClient http;
    private volatile Instant pausedUntil = Instant.EPOCH;
    Clock clock = Clock.systemUTC();

    public CerberusMarketClient(@Value("${guild.market.url:https://cerberus-games.com}") String base,
                                @Value("${guild.market.login:}") String login,
                                @Value("${guild.market.password:}") String password) {
        this.base = base.replaceAll("/+$", "");
        this.login = login;
        this.password = password;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public synchronized Optional<List<MarketPage.Listing>> listings(String code) {
        if (login.isBlank() || clock.instant().isBefore(pausedUntil)) return Optional.empty();
        try {
            MarketPage p = page(code);
            return p == null ? Optional.empty() : Optional.of(p.listings());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            pause(ERROR_PAUSE, "falha ao ler o market: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Uma página logada; null = não conseguiu entrar (já pausado). */
    private MarketPage page(String code) throws Exception {
        String url = base + "/market/?like=" + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&sort=priceup";
        MarketPage p = MarketPage.parse(get(url));
        if (p.loggedIn()) return p;
        if (!signIn()) {
            pause(LOGIN_PAUSE, "não conseguiu entrar no market com a conta " + login);
            return null;
        }
        p = MarketPage.parse(get(url));
        if (!p.loggedIn()) {
            pause(LOGIN_PAUSE, "market continua deslogado depois de entrar");
            return null;
        }
        return p;
    }

    /** Login do XenForo: token CSRF da página de login, depois POST /login/login. */
    private boolean signIn() throws Exception {
        Element html = Jsoup.parse(get(base + "/login/")).selectFirst("html");
        String token = html == null ? "" : html.attr("data-csrf");
        if (token.isEmpty()) return false;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("login", login);
        form.put("password", password);
        form.put("remember", "1");
        form.put("_xfToken", token);
        form.put("_xfRedirect", base + "/market");
        String body = form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        HttpResponse<String> response = http.send(request(base + "/login/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        boolean ok = MarketPage.parse(response.body()).loggedIn();
        if (ok) log.info("Market da Cerberus: entrou com a conta {}", login);
        return ok;
    }

    private String get(String url) throws Exception {
        HttpResponse<String> response = http.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        // 403 é a página de login do XenForo (deslogado): o corpo diz isso pelo data-logged-in.
        if (response.statusCode() >= 500) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9");
    }

    private void pause(Duration duration, String reason) {
        pausedUntil = clock.instant().plus(duration);
        log.warn("Market da Cerberus pausado por {}: {}", duration, reason);
    }
}
