package com.guild.app.member.dto;

import java.time.LocalDate;

/** {@code until}: last day (inclusive, Brasília) the account stays blocked for sale; null removes the block. */
public record SellBlockRequest(LocalDate until) {}
