package com.guild.app.rotation.controller;

import com.guild.app.rotation.dto.RotationDtos.*;
import com.guild.app.rotation.service.RotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rotation/bosses")
@RequiredArgsConstructor
public class RotationController {
    private final RotationService service;
    @GetMapping public Schedule read() { return service.read(); }
    /** Para a DLL do jogo (sem login): PBs da nossa guilda, em texto, pelo nome do personagem. */
    @GetMapping(value="/game",produces="text/plain;charset=UTF-8")
    public ResponseEntity<String> game(@RequestParam(required=false) String character) {
        return service.gameText(character).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());
    }
    @PutMapping @PreAuthorize("hasAnyRole('ADMINISTRADOR','LIDER')")
    public Schedule update(@Valid @RequestBody Update request) { return service.update(request); }
}
