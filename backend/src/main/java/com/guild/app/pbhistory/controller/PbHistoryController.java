package com.guild.app.pbhistory.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.pbhistory.service.PbHistoryService;
import com.guild.app.pbhistory.service.PbHistoryService.Attendance;
import com.guild.app.pbhistory.service.PbHistoryService.Kill;
import com.guild.app.pbhistory.service.PbHistoryService.Overview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Histórico de PB. /game é da DLL (sem login, liberada no SecurityConfig), pelo nome do personagem; o resto é do site. */
@RestController
@RequestMapping("/api/pb-history")
@RequiredArgsConstructor
public class PbHistoryController {
    private final PbHistoryService service;

    public record BossRequest(@NotBlank @Size(max = 80) String name, @Size(max = 64) String label,
                              @NotBlank @Pattern(regexp = "ROTACAO|EXTRA") String kind) {}
    public record NameRequest(@NotBlank @Size(max = 80) String name) {}

    /** Bosses e candidatos vistos pela DLL no mapa {@code map}; devolve a lista de bosses acompanhados. */
    @PostMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public String game(@RequestParam(required = false) String character, @RequestParam int map,
                       @RequestBody(required = false) String body) {
        return service.game(character, map, body);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Overview overview() { return service.overview(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/kills")
    public List<Kill> kills(@RequestParam(required = false) String boss, @RequestParam(required = false) Long memberId,
                            @RequestParam(required = false) Long before, @RequestParam(defaultValue = "50") int limit) {
        return service.kills(boss, memberId, before, limit, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/attendance")
    public Attendance attendance(@RequestParam(defaultValue = "30") int days) {
        return service.attendance(days, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/bosses")
    public Overview saveBoss(@Valid @RequestBody BossRequest body) {
        return service.saveBoss(body.name(), body.label(), body.kind(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/bosses")
    public Overview removeBoss(@RequestParam String name) { return service.removeBoss(name, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/candidates/ignore")
    public Overview ignore(@Valid @RequestBody NameRequest body) {
        return service.ignoreCandidate(body.name(), SecurityUtils.currentMember());
    }
}
