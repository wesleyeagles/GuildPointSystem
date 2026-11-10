package com.guild.app.equipment.controller;

import com.guild.app.equipment.dto.EquipmentDtos.ClientRelics;
import com.guild.app.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Relics do personagem enviadas pela DLL do cliente: {character, relics:[{id, upgrade}]}.
 * Sem autenticação, associada pelo nome do personagem como o equipamento. Liberado no SecurityConfig.
 */
@RestController
@RequestMapping("/api/relic-status")
@RequiredArgsConstructor
public class RelicStatusController {
    private final EquipmentService service;
    @PostMapping("/ingest")
    public Map<String,Object> ingest(@Valid @RequestBody ClientRelics request) { return service.syncRelicsFromClient(request); }
}
