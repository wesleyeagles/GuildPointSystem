package com.guild.app.pbstatus;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Status dos PBs (kbase) para o painel da DLL; sem login, liberado no SecurityConfig. Formato em {@link PbStatusService#forGame}. */
@RestController
@RequestMapping("/api/pb-status/game")
@RequiredArgsConstructor
public class PbStatusController {

    private final PbStatusService service;

    @GetMapping(produces = "text/plain;charset=UTF-8")
    public String status(@RequestParam(required = false) String character, @RequestParam(required = false) Long since) {
        return service.forGame(character, since);
    }
}
