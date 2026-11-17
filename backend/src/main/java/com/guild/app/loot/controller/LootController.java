package com.guild.app.loot.controller;

import com.guild.app.loot.service.LootService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Loot dos membros. POST /game é da DLL (sem login, liberado no SecurityConfig): linhas do chat do sistema do jogo,
 * uma por linha, pelo nome do personagem; responde quantas viraram loot novo. GET é do site (Administrador+).
 */
@RestController
@RequestMapping("/api/loot")
@RequiredArgsConstructor
public class LootController {
    private final LootService service;

    @PostMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public String game(@RequestParam(required = false) String character, @RequestBody(required = false) String body) {
        return Integer.toString(service.ingest(character, body));
    }

    @GetMapping
    public LootService.Page query(@RequestParam(required = false) String nickname,
                                       @RequestParam(required = false) String item,
                                       @RequestParam(required = false) String kind,
                                       @RequestParam(defaultValue = "7") int days,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "" + LootService.PAGE_SIZE) int size) {
        return service.query(nickname, item, kind, days, page, size);
    }
}
