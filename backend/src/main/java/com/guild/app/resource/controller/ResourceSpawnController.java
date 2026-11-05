package com.guild.app.resource.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.resource.service.ResourceSpawnService;
import com.guild.app.resource.service.ResourceSpawnService.Overview;
import com.guild.app.resource.service.ResourceSpawnService.SpawnView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mapa de recursos. /game é da DLL (sem login, liberada no SecurityConfig), pelo nome do personagem;
 * o resto é do site.
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceSpawnController {
    private final ResourceSpawnService service;

    public record AccessRequest(@Size(max = 500) List<Long> memberIds) {}
    public record TierRequest(@NotBlank @Size(max = 64) String name, @NotNull @Min(0) @Max(3) Integer tier) {}

    /** Mobs vistos pela DLL no mapa {@code map}; devolve os pontos do mapa (só para quem está na lista). */
    @PostMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public String game(@RequestParam(required = false) String character, @RequestParam int map,
                       @RequestParam(defaultValue = "0") long since, @RequestBody(required = false) String body) {
        return service.game(character, map, since, body);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Overview overview() { return service.overview(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/spawns")
    public List<SpawnView> spawns(@RequestParam int map) { return service.spawns(map, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/history")
    public ResourceSpawnService.HistoryPage history(@RequestParam int map,
            @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "500") int limit) {
        return service.history(map, after, limit, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/access")
    public Overview updateAccess(@Valid @RequestBody AccessRequest body) {
        return service.updateAccess(body.memberIds(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/mobs")
    public Overview setTier(@Valid @RequestBody TierRequest body) {
        return service.setTier(body.name(), body.tier(), SecurityUtils.currentMember());
    }
}
