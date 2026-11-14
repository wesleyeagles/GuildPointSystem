package com.guild.app.item.controller;

import com.guild.app.item.service.GameItemCard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Ícone de item para os cards da DLL (sem login, liberado no SecurityConfig): 32x32 BGRA cru, linha de cima
 * primeiro (4096 bytes). Parâmetros vêm do campo de ícone das respostas de leilão/market ("sprite|ícone|colunas").
 */
@RestController
@RequiredArgsConstructor
public class GameIconController {
    private final GameItemCard cards;

    @GetMapping("/api/game/icon")
    public ResponseEntity<byte[]> icon(@RequestParam String sheet, @RequestParam int icon, @RequestParam int cols) {
        return cards.icon(sheet, icon, cols)
            .map(b -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1))).body(b))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
