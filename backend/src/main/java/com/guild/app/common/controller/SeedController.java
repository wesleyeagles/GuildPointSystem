package com.guild.app.common.controller;

import com.guild.app.item.repository.ItemSeedRepository;
import com.guild.app.member.dto.SeedResponse;
import com.guild.app.member.repository.CharacterClassRepository;
import com.guild.app.member.repository.GameRaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seeds")
@RequiredArgsConstructor
public class SeedController {

    private final GameRaceRepository gameRaceRepository;
    private final CharacterClassRepository characterClassRepository;
    private final ItemSeedRepository itemSeedRepository;

    @GetMapping("/races")
    public SeedResponse races() {
        return new SeedResponse(gameRaceRepository.findAll().stream()
                .map(r -> new SeedResponse.SeedItem(r.getId(), r.getName(), null)).toList());
    }

    @GetMapping("/classes")
    public SeedResponse classes(@RequestParam(required = false) Long raceId) {
        var classes = raceId != null
                ? characterClassRepository.findByRaceIdOrderByNameAsc(raceId)
                : characterClassRepository.findAll();
        return new SeedResponse(classes.stream()
                .map(c -> new SeedResponse.SeedItem(c.getId(), c.getName(), c.getImageUrl())).toList());
    }

    @GetMapping("/item")
    public SeedResponse itemSeeds(@RequestParam String category) {
        return new SeedResponse(itemSeedRepository.findByCategoryOrderBySortOrderAscNameAsc(category).stream()
                .map(s -> new SeedResponse.SeedItem(s.getId(), s.getName(), s.getImageUrl())).toList());
    }
}
