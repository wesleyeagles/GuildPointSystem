package com.guild.app.enemy.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.enemy.service.EnemyRadarService;
import com.guild.app.enemy.service.EnemyRadarService.NewGuild;
import com.guild.app.enemy.service.EnemyRadarService.View;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Radar de guilds inimigas no site: a lista (Admin/Líder edita) e quem foi visto nos últimos minutos.
 * A DLL não usa estas rotas: ela recebe a lista e os pontos junto do status (POST /api/raids/game/status, fx=6).
 */
@RestController
@RequestMapping("/api/enemy-radar")
@RequiredArgsConstructor
public class EnemyRadarController {
    private final EnemyRadarService service;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public View view() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/guilds")
    public View add(@RequestBody NewGuild body) { return service.add(body, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/guilds/{id}")
    public View remove(@PathVariable long id) { return service.remove(id, SecurityUtils.currentMember()); }
}
