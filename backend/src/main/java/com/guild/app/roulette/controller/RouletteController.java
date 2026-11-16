package com.guild.app.roulette.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.roulette.service.RouletteService;
import com.guild.app.roulette.service.RouletteService.NewRoulette;
import com.guild.app.roulette.service.RouletteService.View;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Roleta no jogo. O site (Admin/Líder) cria; a DLL busca pelo nome do personagem (sem login, liberado no
 * SecurityConfig) e mostra a roleta no meio da tela.
 */
@RestController
@RequestMapping("/api/roulettes")
@RequiredArgsConstructor
public class RouletteController {
    private final RouletteService service;

    /** Roleta na tela agora (ver {@link RouletteService#format}); 204 = nenhuma para este personagem. */
    @GetMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> game(@RequestParam(required = false) String character) {
        return service.current(character)
            .map(r -> ResponseEntity.ok(RouletteService.format(r)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public View view() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public View create(@RequestBody NewRoulette body) { return service.create(body, SecurityUtils.currentMember()); }
}
