package com.guild.app.party.dto;

import java.time.Instant;

public record PartyQueueAccess(boolean open,boolean manual,Instant nextChangeAt) {}
