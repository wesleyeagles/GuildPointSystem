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
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Item do site (imagem enviada, sem sprite do jogo): a chave é "/uploads/arquivo.png|0|1" e o ícone é a imagem
 * inteira reduzida para 32x32 (centralizada, sem distorcer), lida da pasta de uploads.
 */
@Slf4j
@Service
public class GameItemCard {
    public static final int ICON = 32;
    private static final Pattern RELIC = Pattern.compile("relic(\\d{1,4})");
    private static final Pattern SHEET = Pattern.compile("/(sprites|relics|equipment-sprites|icons)/[A-Za-z0-9_.-]+\\.(png|jpg)");
    /** Imagem enviada no site (UploadController): webp fica de fora (o ImageIO não lê). */
    private static final Pattern UPLOAD = Pattern.compile("/uploads/([A-Za-z0-9_-]+\\.(png|jpe?g))");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String frontend;
    private final Path uploads;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, String> effectNames = new HashMap<>(), effectKinds = new HashMap<>();
    private final Map<Integer, List<double[]>> relicLevel0 = new HashMap<>();
    private final Map<String, byte[]> icons = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) { return size() > 500; }
    });

    public GameItemCard(JdbcTemplate jdbc, ObjectMapper json, @Value("${guild.game.icon-base-url:${guild.oauth.frontend-url:http://localhost:5173}}") String frontend,
                        @Value("${guild.upload.base-path:./uploads}") String uploads) {
        this.jdbc = jdbc;
        this.json = json;
        this.frontend = frontend.replaceAll("/+$", "");
        this.uploads = Path.of(uploads);
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

    /** A DLL descarta a linha inteira com mais de 300 bytes de efeitos (EventCard::MaxEffectsBytes). */
    static final int MAX_EFFECTS = 280;

    /**
     * "Force attack +3.5%, Dodge +1.30" para relic; para os outros itens, os Special Effects do site
     * (item_weapon/armor/accessory, ex.: "Dodge +10"). Vazio se não tem.
     */
    public String effects(String code) {
        String c = catalogCode(code);
        if (c == null || c.isEmpty()) return "";
        Matcher m = RELIC.matcher(c);
        if (!m.matches()) return fit(itemEffects(c));
        List<double[]> level = relicLevel0.get(Integer.parseInt(m.group(1)));
        if (level == null) return "";
        StringJoiner out = new StringJoiner(", ");
        for (double[] e : level) out.add(effect((int) e[0], e[1]));
        return fit(out.toString());
    }

    /** Special Effects de cada item do site (arma/armadura/acessório), por id; item sem efeito fica de fora. */
    public Map<Long, List<String>> effectsByItemId(Collection<Long> ids) {
        Map<Long, List<String>> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (list.isEmpty()) return out;
        String marks = String.join(",", Collections.nCopies(list.size(), "?"));
        jdbc.query("""
            SELECT i.id, COALESCE(w.special_effects, a.special_effects, c.special_effects)::text FROM item i
            LEFT JOIN item_weapon w ON w.item_id=i.id LEFT JOIN item_armor a ON a.item_id=i.id
            LEFT JOIN item_accessory c ON c.item_id=i.id
            WHERE i.id IN (""" + marks + ")", rs -> {
            String raw = rs.getString(2);
            if (raw == null) return;
            try {
                List<String> effects = new ArrayList<>();
                for (JsonNode e : json.readTree(raw)) {
                    String text = e.asText("").strip();
                    if (!text.isEmpty()) effects.add(text);
                }
                if (!effects.isEmpty()) out.put(rs.getLong(1), effects);
            } catch (Exception ignored) {
                // JSON quebrado: o item fica sem efeitos na lista
            }
        }, list.toArray());
        return out;
    }

    private String itemEffects(String code) {
        List<String> rows = jdbc.queryForList("""
            SELECT COALESCE(w.special_effects, a.special_effects, c.special_effects)::text FROM item i
            LEFT JOIN item_weapon w ON w.item_id=i.id LEFT JOIN item_armor a ON a.item_id=i.id
            LEFT JOIN item_accessory c ON c.item_id=i.id
            WHERE lower(i.game_code)=? ORDER BY i.id LIMIT 1""", String.class, code);
        if (rows.isEmpty() || rows.get(0) == null) return "";
        try {
            StringJoiner out = new StringJoiner(", ");
            for (JsonNode e : json.readTree(rows.get(0))) {
                String text = e.asText("").strip();
                if (!text.isEmpty()) out.add(text);
            }
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Uma linha só, sem TAB, e cabendo no limite da DLL (corta no último efeito inteiro). */
    static String fit(String effects) {
        String text = effects.replaceAll("\\p{Cntrl}", " ").strip();
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_EFFECTS) return text;
        String cut = text.substring(0, Math.min(text.length(), MAX_EFFECTS - 3));
        int comma = cut.lastIndexOf(", ");
        if (comma > 0) cut = cut.substring(0, comma);
        while (cut.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_EFFECTS - 3) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
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

    /**
     * Colunas de cada sprite local, como o site recorta (SHEET_LAYOUTS em spriteUtils.ts). Vale mais que o
     * sprite_cols do banco: os anéis/amuletos vêm com 128 no banco, mas o ringseamulets.png tem 32 colunas
     * (células de 64 px em 2048 px); com 128 o recorte saía um pedaço de 16 px e a DLL nem aceitava a chave.
     */
    static final Map<String, Integer> SHEET_COLS = Map.ofEntries(
        Map.entry("/sprites/weapon.png", 32), Map.entry("/sprites/helmet.png", 32), Map.entry("/sprites/upper.png", 32),
        Map.entry("/sprites/lower.png", 32), Map.entry("/sprites/gloves.png", 32), Map.entry("/sprites/shoes.png", 32),
        Map.entry("/sprites/potion.png", 32), Map.entry("/sprites/booty.png", 32), Map.entry("/sprites/ringseamulets.png", 32),
        Map.entry("/sprites/shield.png", 8), Map.entry("/sprites/resource.png", 16), Map.entry("/relics/item-1-6.jpg", 32));

    /** Onde está o ícone do item: "sprite|ícone|colunas" (para GET /api/game/icon), ou vazio. */
    public String iconKey(String code) {
        String c = catalogCode(code);
        if (c == null || c.isEmpty()) return "";
        return jdbc.query("SELECT sprite_sheet, icon_id, sprite_cols FROM game_lua_item WHERE game_code=? LIMIT 1",
                (r, n) -> r.getString(1) + "|" + r.getInt(2) + "|" + SHEET_COLS.getOrDefault(r.getString(1), r.getInt(3)), c).stream()
            .filter(k -> SHEET.matcher(k.substring(0, k.indexOf('|'))).matches()).findFirst().orElse("");
    }

    /** Chave do ícone de uma imagem enviada no site ("/uploads/x.png|0|1"), ou vazio se não dá para usar. */
    public static String uploadIconKey(String imageUrl) {
        return imageUrl != null && UPLOAD.matcher(imageUrl).matches() ? imageUrl + "|0|1" : "";
    }

    /** 32x32 BGRA (de cima para baixo) do ícone; vazio se o sprite não é conhecido ou não baixou. */
    public Optional<byte[]> icon(String sheet, int iconId, int cols) {
        if (sheet == null || iconId < 0 || cols <= 0 || cols > 64) return Optional.empty();
        boolean upload = UPLOAD.matcher(sheet).matches();
        if (upload ? iconId != 0 || cols != 1 : !SHEET.matcher(sheet).matches()) return Optional.empty();
        String key = sheet + "|" + iconId + "|" + cols;
        byte[] cached = icons.get(key);
        if (cached != null) return cached.length == 0 ? Optional.empty() : Optional.of(cached);
        Long failedAt = failures.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_PAUSE_MS) return Optional.empty();
        // Um recorte por vez no servidor inteiro: muitas DLLs pedindo o mesmo ícone novo ao mesmo tempo esperam
        // o primeiro e pegam do cache, em vez de cada uma baixar e decodificar o sprite.
        synchronized (renderLock) {
            cached = icons.get(key);
            if (cached != null) return Optional.of(cached);
            byte[] made = upload ? renderUpload(sheet) : render(sheet, iconId, cols);
            if (made.length == 0) {
                failures.put(key, System.currentTimeMillis());
                return Optional.empty();
            }
            failures.remove(key);
            icons.put(key, made);
            return Optional.of(made);
        }
    }

    private static final long FAILURE_PAUSE_MS = 10 * 60_000;
    private final Object renderLock = new Object();
    private final Map<String, Long> failures = new java.util.concurrent.ConcurrentHashMap<>();
    /** Bytes do sprite (jpg/png comprimido, poucos MB), os últimos 3: baixar de novo a cada ícone novo pesa. */
    private final Map<String, byte[]> sheets = new LinkedHashMap<>(4, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) { return size() > 3; }
    };

    /** Imagem enviada inteira, reduzida para caber em 32x32 sem distorcer, centralizada sobre preto. */
    byte[] renderUpload(String url) {
        Matcher m = UPLOAD.matcher(url);
        if (!m.matches()) return new byte[0];
        Path file = uploads.resolve(m.group(1)).normalize();
        try {
            if (!file.startsWith(uploads.normalize()) || !Files.isRegularFile(file) || Files.size(file) > 5_000_000) return new byte[0];
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return new byte[0];
            double scale = Math.min((double) ICON / img.getWidth(), (double) ICON / img.getHeight());
            int w = Math.max(1, (int) Math.round(img.getWidth() * scale)), h = Math.max(1, (int) Math.round(img.getHeight() * scale));
            BufferedImage out = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, ICON, ICON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, (ICON - w) / 2, (ICON - h) / 2, w, h, null);
            g.dispose();
            return bgra(out);
        } catch (Exception e) {
            log.warn("Ícone do upload {} não saiu: {}", url, e.toString());
            return new byte[0];
        }
    }

    private static byte[] bgra(BufferedImage out) {
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
    }

    private byte[] render(String sheet, int iconId, int cols) {
        try {
            byte[] bytes = sheets.get(sheet);
            if (bytes == null) {
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(frontend + sheet)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200 || r.body().length > 30_000_000) return new byte[0];
                bytes = r.body();
                sheets.put(sheet, bytes);
            }
            // Decodifica só a célula do ícone (o sprite inteiro descomprimido passa de 30 MB).
            BufferedImage img;
            try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return new byte[0];
                var reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    int cellSize = reader.getWidth(0) / cols;
                    int cx = iconId % cols * cellSize, cy = iconId / cols * cellSize;
                    if (cellSize <= 0 || cy + cellSize > reader.getHeight(0)) return new byte[0];
                    var param = reader.getDefaultReadParam();
                    param.setSourceRegion(new java.awt.Rectangle(cx, cy, cellSize, cellSize));
                    img = reader.read(0, param);
                } finally {
                    reader.dispose();
                }
            }
            if (img == null) return new byte[0];
            int cell = img.getWidth(), x = 0, y = 0;
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
