package com.guild.app.pbstatus;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabela "Статус ПБ" da kbase da Cerberus (https://cerberus-games.com/kbase/pb/): HTML pronto, sem API. Cada mapa
 * abre com uma linha "block-header" (nome russo); cada boss tem nome, status e a hora de nascer.
 * <p>
 * Duplicatas: o mesmo boss aparece 2–3 vezes no mesmo mapa, uma linha por agendamento (17/11 00:00, 17/11 20:00,
 * 21/11 20:00…), todas com a mesma morte. {@link #dedupe} deixa uma linha por (mapa, codigo): vivo se alguma linha
 * disser vivo, senao a hora mais cedo. As horas da pagina sao de Moscou (UTC+3), como os anuncios do servidor.
 */
public final class PbStatusPage {
    static final ZoneId SERVER_ZONE = ZoneOffset.ofHours(3);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern MONSTER = Pattern.compile("/kbase/monsters/([0-9A-Fa-f]+)");
    private static final Pattern KILLED = Pattern.compile("(?i)^killed by\\s+(.*?)\\s*(?:\\[(.*)])?$");

    /** Mapas da pagina (nome russo) com o nome do mapa no cliente (en-gb/NDMap.edf), na ordem da pagina. */
    static final Map<String, String> MAPS;
    static {
        var m = new LinkedHashMap<String, String>();
        m.put("Вулкан", "Volcano");
        m.put("Краговые Шахты", "Crag Mine");
        m.put("Сеттова Пустыня", "Sette Desert");
        m.put("Платформа Этер", "Ether");
        m.put("Горы Чудовищ", "Beast Mountain");
        m.put("Лаборатория Картелы", "Laboratory");
        m.put("Элан", "Elan Plateau");
        m.put("Солус", "Solus");
        m.put("Анакаде", "Anacaade");
        m.put("Харам", "Haram");
        m.put("Нумерус", "Numerus");
        m.put("Застава-213", "Armory-213");
        m.put("Застава-117", "Armory-117");
        m.put("Генштаб Федерации", "Bellato HQ");
        m.put("Генштаб Альянса", "Cora HQ");
        m.put("Генеральный Штаб Империи", "Accretia HQ");
        MAPS = Collections.unmodifiableMap(m);
    }

    public enum State { LIVE, DEAD, KILLED }

    /** Uma linha da pagina. respawn null = "Не запланировано" (vivo, sem agendamento). */
    public record Row(String region, String code, String name, State state, String killer, String guild, Instant respawn) {}

    /** Um boss por mapa, depois de juntar as linhas repetidas. */
    public record Boss(String map, String code, String name, State state, String killer, String guild, Instant respawn) {
        public boolean guildPb() { return name.startsWith("[ПБ Гильдий]"); }
    }

    private PbStatusPage() {}

    public static List<Row> parse(String html) {
        var rows = new ArrayList<Row>();
        String region = null;
        for (Element tr : Jsoup.parse(html).select("table.dataList-table tr")) {
            Element header = tr.selectFirst("td.block-header");
            if (header != null) { region = header.text().strip(); continue; }
            if (region == null || tr.selectFirst("td.table-header") != null) continue;
            var cells = tr.select("> td");
            if (cells.size() < 3) continue;
            Element link = cells.get(0).selectFirst("a[href]");
            Matcher code = link == null ? null : MONSTER.matcher(link.attr("href"));
            if (code == null || !code.find()) continue;
            String status = cells.get(1).text().strip();
            State state;
            String killer = "", guild = "";
            Matcher killed = KILLED.matcher(status);
            if (killed.matches()) {
                state = State.KILLED;
                killer = killed.group(1).strip();
                guild = killed.group(2) == null ? "" : killed.group(2).strip();
            } else if (status.equalsIgnoreCase("Live")) state = State.LIVE;
            else state = State.DEAD;
            rows.add(new Row(region, code.group(1).toUpperCase(Locale.ROOT), link.text().strip(), state, killer, guild,
                    respawn(cells.get(2).text().strip())));
        }
        return rows;
    }

    private static Instant respawn(String text) {
        try {
            return LocalDateTime.parse(text, WHEN).atZone(SERVER_ZONE).toInstant();
        } catch (RuntimeException e) {
            return null;  // "Не запланировано"
        }
    }

    /** Uma linha por (mapa, codigo), na ordem em que o boss aparece primeiro; mapas desconhecidos ficam com o nome russo. */
    public static List<Boss> dedupe(List<Row> rows) {
        var byKey = new LinkedHashMap<String, Boss>();
        for (Row r : rows) {
            String map = MAPS.getOrDefault(r.region(), r.region());
            Boss next = new Boss(map, r.code(), r.name(), r.state(), r.killer(), r.guild(), r.respawn());
            byKey.merge(map + "\t" + r.code(), next, PbStatusPage::pick);
        }
        return List.copyOf(byKey.values());
    }

    /** Vivo vence; entre mortos fica o nascimento mais cedo (o proximo); sem hora perde para com hora. */
    private static Boss pick(Boss a, Boss b) {
        if (a.state() == State.LIVE) return a;
        if (b.state() == State.LIVE) return b;
        if (a.respawn() == null) return b.respawn() == null ? a : b;
        if (b.respawn() == null) return a;
        return b.respawn().isBefore(a.respawn()) ? b : a;
    }
}
