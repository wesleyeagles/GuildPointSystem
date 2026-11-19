package com.guild.app.dll.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.dll.service.DllDisplaySettingsService;
import com.guild.app.dll.service.DllDisplaySettingsService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin -> DLL: ajustes visuais que a DLL de todos aplica (tamanho das setas da PT e da guild do jogo). */
@RestController
@RequestMapping("/api/dll/display")
@RequiredArgsConstructor
public class DllDisplaySettingsController {

    private final DllDisplaySettingsService service;

    public record UpdateRequest(
            @Min(DllDisplaySettingsService.MIN_SCALE) @Max(DllDisplaySettingsService.MAX_SCALE) int partyArrowScale,
            @Min(DllDisplaySettingsService.MIN_SCALE) @Max(DllDisplaySettingsService.MAX_SCALE) int guildArrowScale) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public View get() { return service.view(); }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public View update(@Valid @RequestBody UpdateRequest body) {
        return service.update(body.partyArrowScale(), body.guildArrowScale(), SecurityUtils.currentMember());
    }
}
