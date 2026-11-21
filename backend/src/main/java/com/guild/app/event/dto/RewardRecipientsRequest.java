package com.guild.app.event.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record RewardRecipientsRequest(@NotEmpty @Size(max=500) List<@NotNull @Positive Long> claimIds) {}
