package com.guild.app.event.dto;
import java.util.List;
public record RewardRecipientsResponse(String name, List<Recipient> recipients) {
    public record Recipient(Long memberId, String nickname, List<Long> claimIds) {}
}
