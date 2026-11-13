package com.guild.app.common.enums;

public enum AuctionStatus {
    /** Not started: opens at {@code startsAt}, or by hand when it has none ("em breve"). No bids. */
    SCHEDULED,
    /** Timer frozen by a leader/admin: no bids, reserves stay held. */
    PAUSED,
    OPEN,
    DOLE,
    TIE_BREAK,
    CLOSED,
    /** Force-closed by a leader/admin: no winner, nobody charged. */
    CANCELLED
}
