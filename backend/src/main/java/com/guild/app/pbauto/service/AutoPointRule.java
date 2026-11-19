package com.guild.app.pbauto.service;

import java.time.*;
import java.util.List;

/**
 * Uma regra de pontos automáticos (tabela auto_point_rule, Admin → Pontos automáticos). Horários de Brasília.
 * {@code rg} e {@code presence} são opcionais (null = desligado), mas pelo menos um existe.
 *
 * @param weekdays     bit 0 = segunda … bit 6 = domingo; o dia é o do início (RG, ou começo da presença)
 * @param shoutsBefore minutos antes do início em que o "Sistema" manda {@code shout} no jogo
 */
public record AutoPointRule(long id, String code, String label, boolean enabled, int weekdays,
                            List<Integer> shoutsBefore, String shout, String shoutEn, Rg rg, Presence presence) {

    /** Quem está no local (envio da DLL com até 3 min) no horário ganha {@code points} Pontos de RG. */
    public record Rg(LocalTime at, String map, List<String> regions, long points) {}

    /** Quem a DLL viu no local entre {@code from} e {@code to} ganha o objetivo {@code objective}. */
    public record Presence(LocalTime from, LocalTime to, String map, List<String> regions, String objective) {}

    static final ZoneId BRT = PbAutoService.BRT;

    public boolean runsOn(LocalDate day) {
        return (weekdays & (1 << (day.getDayOfWeek().getValue() - 1))) != 0;
    }

    /** Início da rodada do dia: horário do RG, ou o começo da presença sem RG. Os shouts contam a partir dele. */
    public LocalDateTime start(LocalDate day) {
        return day.atTime(rg != null ? rg.at() : presence.from());
    }

    public Instant rgAt(LocalDate day) { return at(day.atTime(rg.at())); }

    /** Começo da presença: antes do horário do RG quer dizer depois da meia-noite (dia seguinte). */
    public LocalDateTime presenceFrom(LocalDate day) {
        LocalDateTime from = day.atTime(presence.from());
        return rg != null && presence.from().isBefore(rg.at()) ? from.plusDays(1) : from;
    }

    /** Fim da presença: igual ou antes do começo quer dizer depois da meia-noite. */
    public LocalDateTime presenceTo(LocalDate day) {
        LocalDateTime from = presenceFrom(day);
        LocalDateTime to = from.toLocalDate().atTime(presence.to());
        return to.isAfter(from) ? to : to.plusDays(1);
    }

    static Instant at(LocalDateTime time) { return time.atZone(BRT).toInstant(); }

    /** Mapa igual e uma das regiões (sem região = o mapa inteiro), sem diferença de maiúscula. */
    static boolean matches(String map, String region, String wantedMap, List<String> wantedRegions) {
        if (!same(map, wantedMap)) return false;
        return wantedRegions.isEmpty() || wantedRegions.stream().anyMatch(r -> same(region, r));
    }

    static boolean same(String a, String b) { return a != null && b != null && a.strip().equalsIgnoreCase(b.strip()); }
}
