package com.guild.app.member.dto;

import java.util.List;

public record SeedResponse(List<SeedItem> items) {

    public record SeedItem(Long id, String name, String imageUrl) {}
}
