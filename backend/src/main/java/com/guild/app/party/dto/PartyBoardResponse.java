package com.guild.app.party.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code lfg} is ordered by priority points, then queue position. {@code lfgLocked}: members cannot join
 * the waiting list on their own (Administrador/Líder closed it). {@code serverTime} lets clients run the
 * waiting timers on the server clock and discard a board snapshot older than the one they hold.
 */
public record PartyBoardResponse(
        List<PartySummary> parties,
        List<PartyLfgEntry> lfg,
        boolean lfgLocked,
        MyPartyState my,
        Instant serverTime,
        PartyQueueAccess queueAccess
) {}
