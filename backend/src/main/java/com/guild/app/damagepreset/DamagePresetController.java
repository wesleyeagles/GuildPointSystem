package com.guild.app.damagepreset;

import com.fasterxml.jackson.databind.JsonNode;
import com.guild.app.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Presets da calculadora de dano: cada membro vê e mexe só nos seus. */
@RestController
@RequestMapping("/api/damage-presets")
@RequiredArgsConstructor
public class DamagePresetController {

    private final DamagePresetService service;

    public record PresetRequest(
            @NotBlank @Size(max = DamagePresetService.NAME_MAX) String name,
            @NotNull JsonNode state) {}

    @GetMapping
    public List<DamagePresetService.Preset> list() {
        return service.list(SecurityUtils.currentMember().getId());
    }

    /** Cria, ou substitui o de mesmo nome (sem diferenciar maiúscula). */
    @PutMapping
    public List<DamagePresetService.Preset> save(@Valid @RequestBody PresetRequest request) {
        return service.save(SecurityUtils.currentMember().getId(), request.name(), request.state());
    }

    /** Sobe vários de uma vez (os que estavam só no navegador); os de mesmo nome já na conta ficam. */
    @PostMapping("/import")
    public List<DamagePresetService.Preset> importAll(@RequestBody List<@Valid PresetRequest> request) {
        return service.importAll(SecurityUtils.currentMember().getId(),
                request.stream().map(r -> new DamagePresetService.Preset(r.name(), r.state(), null)).toList());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.OK)
    public List<DamagePresetService.Preset> delete(@RequestParam String name) {
        return service.delete(SecurityUtils.currentMember().getId(), name);
    }
}
