package com.guild.app.marker;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Seta de marcação pela DLL (sem login, liberado no SecurityConfig), pelo nome do personagem.
 * GET: "id&lt;TAB&gt;tipo&lt;TAB&gt;serial" (tipo 0 jogador, 1 monstro; -1 = seta tirada), 204 = nenhuma.
 * Com dead=id (DLL 1.54.0+): o alvo dessa marcação morreu; a manual some e a automática (radar) vai para o próximo.
 * POST (Líder/Administrador): corpo "tipo&lt;TAB&gt;serial" para marcar ou "clear" para tirar; 204, 403 ou 400.
 */
@RestController
@RequestMapping("/api/marker/game")
@RequiredArgsConstructor
public class MarkerGameController {

    private final MarkerService service;

    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> current(@RequestParam(required = false) String character,
                                          @RequestParam(required = false) Long dead) {
        return service.forGame(character, dead)
            .map(m -> ResponseEntity.ok(m.id() + "\t" + m.kind() + "\t" + m.serial() + "\n"))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(consumes = "text/plain")
    public ResponseEntity<Void> set(@RequestParam(required = false) String character,
                                    @RequestBody(required = false) String body) {
        String text = body == null ? "" : body.strip();
        int kind;
        long serial = 0;
        try {
            if (text.equalsIgnoreCase("clear")) kind = -1;
            else {
                String[] parts = text.split("\t");
                if (parts.length != 2) return ResponseEntity.badRequest().build();
                kind = Integer.parseInt(parts[0].strip());
                serial = Long.parseLong(parts[1].strip());
            }
            return service.set(character, kind, serial).isPresent()
                ? ResponseEntity.noContent().build() : ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
