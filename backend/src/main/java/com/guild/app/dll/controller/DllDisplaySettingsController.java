package com.guild.app.dll.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.dll.service.DllDisplaySettingsService;
import com.guild.app.dll.service.DllDisplaySettingsService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Admin -> DLL: ajustes visuais que a DLL de todos aplica (setas da PT, da guild e de marcação). */
@RestController
@RequestMapping("/api/dll/display")
@RequiredArgsConstructor
public class DllDisplaySettingsController {

    private final DllDisplaySettingsService service;

    public record UpdateRequest(
            @Min(DllDisplaySettingsService.MIN_SCALE) @Max(DllDisplaySettingsService.MAX_SCALE) int partyArrowScale,
            @Min(DllDisplaySettingsService.MIN_SCALE) @Max(DllDisplaySettingsService.MAX_SCALE) int guildArrowScale,
            @Min(DllDisplaySettingsService.MIN_DISTANCE) @Max(DllDisplaySettingsService.MAX_DISTANCE) Integer arrowDistance,
            @Min(DllDisplaySettingsService.MIN_SCALE) @Max(DllDisplaySettingsService.MAX_SCALE) Integer markerArrowScale,
            @Pattern(regexp = "#?[0-9A-Fa-f]{6}") String markerColor) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public View get() { return service.view(); }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public View update(@Valid @RequestBody UpdateRequest body) {
        return service.update(new DllDisplaySettingsService.Update(body.partyArrowScale(), body.guildArrowScale(),
                body.arrowDistance() == null ? DllDisplaySettingsService.DEFAULT_DISTANCE : body.arrowDistance(),
                body.markerArrowScale(), body.markerColor()), SecurityUtils.currentMember());
    }
}
