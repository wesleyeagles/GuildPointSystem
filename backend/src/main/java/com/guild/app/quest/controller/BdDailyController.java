package com.guild.app.quest.controller;

import com.guild.app.quest.dto.BdCompletionRequest;
import com.guild.app.quest.service.BdDailyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class BdDailyController {
    private final BdDailyService service;
    @PostMapping("/api/quest-status/bd/ingest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ingest(@Valid @RequestBody BdCompletionRequest request) { service.ingest(request); }
    @GetMapping("/api/quests/bd")
    public BdDailyService.History history(@RequestParam(required = false) LocalDate day) { return service.forSite(day); }
    @GetMapping(value = "/api/quest-status/bd/game", produces = "text/plain;charset=UTF-8")
    public String game(@RequestParam String character) { return service.forGame(character); }
}
