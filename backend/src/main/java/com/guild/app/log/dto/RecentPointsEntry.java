package com.guild.app.log.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Uma rodada de pontos para a guild, sem nomes de jogador: um evento (todos os resgates dele), uma
 * rodada de RG num local, o bônus da CW ou pontos por proximidade.
 *
 * @param kind     EVENT, RG, CW ou PROXIMITY
 * @param name     nome do evento, "RG" ou o motivo
 * @param detail   local do RG; null nos outros
 * @param members  quantos membros receberam
 * @param points   pontos de cada um (o maior, se variou)
 * @param total    soma dos pontos dados
 * @param at       último ponto dado na rodada
 * @param received o membro logado recebeu
 * @param myPoints quanto ele recebeu; null se não recebeu
 * @param limited  não recebeu porque já estava no limite do dia (evento LIMITADO ou RG)
 * @param claimIds resgates de evento da rodada (para o Admin cancelar); null para quem não é Admin ou sem resgate
 */
public record RecentPointsEntry(
        String kind,
        String name,
        String detail,
        int members,
        BigDecimal points,
        BigDecimal total,
        Instant at,
        boolean received,
        BigDecimal myPoints,
        boolean limited,
        List<Long> claimIds) {
}
