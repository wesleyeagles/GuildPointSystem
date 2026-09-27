package com.guild.app.party.dto;

import jakarta.validation.constraints.Size;

public record UpsertLfgRequest(@Size(max = 200) String note) {}
