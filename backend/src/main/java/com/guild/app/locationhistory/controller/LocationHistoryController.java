package com.guild.app.locationhistory.controller;

import com.guild.app.locationhistory.service.LocationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Onde cada membro estava num horário (histórico de 3 dias da DLL). Administrador+. */
@RestController
@RequestMapping("/api/location-history")
@RequiredArgsConstructor
public class LocationHistoryController {
    private final LocationHistoryService service;

    @GetMapping
    public List<LocationHistoryService.Row> query(@RequestParam Instant at,
                                                  @RequestParam(required = false) String nickname,
                                                  @RequestParam(defaultValue = "10") int minutes) {
        return service.query(at, nickname, minutes);
    }
}
