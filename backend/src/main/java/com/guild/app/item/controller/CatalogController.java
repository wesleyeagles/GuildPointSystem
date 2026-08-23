package com.guild.app.item.controller;

import com.guild.app.item.dto.*;
import com.guild.app.item.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/accessories")
    public List<GameAccessoryResponse> listAccessories(
            @RequestParam(required = false) String subtype,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String civilMask,
            @RequestParam(required = false) String search) {
        return catalogService.listAccessories(subtype, grade, civilMask, search);
    }

    @GetMapping("/accessories/{gameCode}")
    public GameAccessoryResponse getAccessory(@PathVariable String gameCode) {
        return catalogService.getAccessory(gameCode);
    }

    @GetMapping("/armor")
    public List<GameArmorResponse> listArmor(
            @RequestParam(required = false) String slot,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String civilMask,
            @RequestParam(required = false) Integer minLevel,
            @RequestParam(required = false) String search) {
        return catalogService.listArmor(slot, grade, civilMask, minLevel, search);
    }

    @GetMapping("/armor/{gameCode}")
    public GameArmorResponse getArmor(@PathVariable String gameCode) {
        return catalogService.getArmor(gameCode);
    }

    @GetMapping("/effects")
    public List<EffectDefinitionResponse> listEffects() {
        return catalogService.listEffects();
    }

    @GetMapping("/sets")
    public List<ItemSetResponse> listSets(@RequestParam(required = false) String gameCode) {
        if (gameCode != null && !gameCode.isBlank()) {
            return catalogService.listSetsForGameCode(gameCode);
        }
        return catalogService.listAllSets();
    }
}
