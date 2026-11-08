package com.guild.app.roster;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Lista de membros para a DLL (sem login, liberado no SecurityConfig), pelo nome do personagem.
 * Texto UTF-8, uma linha por membro: "nick TAB online(0/1) TAB mapa TAB regiao TAB parado TAB visto TAB exp",
 * tempos em segundos ate agora (-1 = sem dado). 403 se o personagem nao e de Lider/Administrador.
 */
@RestController
@RequestMapping("/api/members/game/roster")
@RequiredArgsConstructor
public class MemberRosterController {

    private final MemberRosterService service;

    @GetMapping(produces = "text/plain;charset=UTF-8")
    public String roster(@RequestParam(required = false) String character) {
        return service.forGame(character);
    }
}
