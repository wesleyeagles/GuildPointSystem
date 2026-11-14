package com.guild.app.item.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O que os cards da DLL (leilão e market) mostram de um item além do nome: efeitos da relic (+0, como na
 * janela Relics, em forma curta: "Force attack +3.5%") e o ícone, recortado do mesmo sprite que o site usa
 * (game_lua_item.sprite_sheet + icon_id, baixado do frontend) e entregue como 32x32 BGRA cru, pronto para
 * o StretchDIBits. Dados das relics: game/relicData.json (cópia do frontend, lido do cliente).
 */
@Slf4j
@Service
public class GameItemCard {
    public static final int ICON = 32;
    private static final Pattern RELIC = Pattern.compile("relic(\\d{1,4})");
    private static final Pattern SHEET = Pattern.compile("/(sprites|relics|equipment-sprites|icons)/[A-Za-z0-9_.-]+\\.(png|jpg)");

    private final JdbcTemplate jdbc;
    private final String frontend;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, String> effectNames = new HashMap<>(), effectKinds = new HashMap<>();
    private final Map<Integer, List<double[]>> relicLevel0 = new HashMap<>();
    private final Map<String, byte[]> icons = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) { return size() > 500; }
    });

    public GameItemCard(JdbcTemplate jdbc, ObjectMapper json, @Value("${guild.game.icon-base-url:${guild.oauth.frontend-url:http://localhost:5173}}") String frontend) {
        this.jdbc = jdbc;
        this.frontend = frontend.replaceAll("/+$", "");
        try (InputStream in = new ClassPathResource("game/relicData.json").getInputStream()) {
            JsonNode root = json.readTree(in);
            root.path("effects").fields().forEachRemaining(e -> {
                effectNames.put(e.getKey(), e.getValue().path("name").asText());
                effectKinds.put(e.getKey(), e.getValue().path("kind").asText("number"));
            });
            root.path("relics").fields().forEachRemaining(e -> {
                List<double[]> level = new ArrayList<>();
                for (JsonNode pair : e.getValue().path("levels").path(0)) level.add(new double[]{pair.path(0).asDouble(), pair.path(1).asDouble()});
                relicLevel0.put(Integer.parseInt(e.getKey()), level);
            });
        } catch (Exception e) {
            log.warn("relicData.json não carregou: {}", e.getMessage());
        }
    }

    /** Código do catálogo (relic020) ou do jogo/market (iyfip06) -> código do catálogo. */
    static String catalogCode(String code) {
        if (code == null) return null;
        String c = code.strip().toLowerCase(Locale.ROOT);
        String relic = AuctionRelicCodes.catalogCode(c);
        return relic != null ? relic : c;
    }

    /** "Force attack +3.5%, Dodge +1.30" para relic; vazio para o resto. */
    public String effects(String code) {
        String c = catalogCode(code);
        if (c == null) return "";
        Matcher m = RELIC.matcher(c);
        if (!m.matches()) return "";
        List<double[]> level = relicLevel0.get(Integer.parseInt(m.group(1)));
        if (level == null) return "";
        StringJoiner out = new StringJoiner(", ");
        for (double[] e : level) out.add(effect((int) e[0], e[1]));
        return out.toString();
    }

    String effect(int code, double value) {
        String key = String.valueOf(code);
        String name = effectNames.getOrDefault(key, "Effect #" + code);
        double size = Math.abs(value);
        String amount = switch (effectKinds.getOrDefault(key, "number")) {
            case "percent" -> String.format(Locale.ROOT, "%.1f%%", size * 100);
            case "seconds" -> String.format(Locale.ROOT, "%.2fs", size / 1000);
            case "integer" -> String.valueOf(Math.round(size));
            default -> String.format(Locale.ROOT, "%.2f", size);
        };
        return name + " " + (value < 0 ? "-" : "+") + amount;
    }

    /** Onde está o ícone do item: "sprite|ícone|colunas" (para GET /api/game/icon), ou vazio. */
    public String iconKey(String code) {
        String c = catalogCode(code);
        if (c == null || c.isEmpty()) return "";
        return jdbc.query("SELECT sprite_sheet, icon_id, sprite_cols FROM game_lua_item WHERE game_code=? LIMIT 1",
                (r, n) -> r.getString(1) + "|" + r.getInt(2) + "|" + r.getInt(3), c).stream()
            .filter(k -> SHEET.matcher(k.substring(0, k.indexOf('|'))).matches()).findFirst().orElse("");
    }

    /** 32x32 BGRA (de cima para baixo) do ícone; vazio se o sprite não é conhecido ou não baixou. */
    public Optional<byte[]> icon(String sheet, int iconId, int cols) {
        if (sheet == null || !SHEET.matcher(sheet).matches() || iconId < 0 || cols <= 0 || cols > 64) return Optional.empty();
        String key = sheet + "|" + iconId + "|" + cols;
        byte[] cached = icons.get(key);
        if (cached != null) return Optional.of(cached);
        byte[] made = render(sheet, iconId, cols);
        if (made.length == 0) return Optional.empty();   // falha não fica guardada: tenta de novo no próximo pedido
        icons.put(key, made);
        return Optional.of(made);
    }

    private byte[] render(String sheet, int iconId, int cols) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(frontend + sheet)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) return new byte[0];
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
            if (img == null) return new byte[0];
            int cell = img.getWidth() / cols;
            int x = iconId % cols * cell, y = iconId / cols * cell;
            if (cell <= 0 || y + cell > img.getHeight()) return new byte[0];
            BufferedImage out = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, ICON, ICON, x, y, x + cell, y + cell, null);
            g.dispose();
            byte[] bgra = new byte[ICON * ICON * 4];
            for (int py = 0; py < ICON; py++)
                for (int px = 0; px < ICON; px++) {
                    int argb = out.getRGB(px, py), i = (py * ICON + px) * 4;
                    bgra[i] = (byte) argb;
                    bgra[i + 1] = (byte) (argb >> 8);
                    bgra[i + 2] = (byte) (argb >> 16);
                    bgra[i + 3] = (byte) 0xFF;
                }
            return bgra;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new byte[0];
        } catch (Exception e) {
            log.warn("Ícone {} #{} não saiu: {}", sheet, iconId, e.toString());
            return new byte[0];
        }
    }
}
