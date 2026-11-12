package com.guild.app.event.controller;

import com.guild.app.common.exception.AppException;
import com.guild.app.event.service.EventGameService;
import com.guild.app.shout.service.GameLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * Card de evento dentro do jogo, todo pela DLL (sem login, liberado no SecurityConfig), pelo nome do
 * personagem. Respostas em text/plain UTF-8.
 */
@RestController
@RequestMapping("/api/events/game")
@RequiredArgsConstructor
public class EventGameController {

    private static final MediaType TEXT = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final EventGameService service;
    private final GameLanguage language;

    /** Uma linha por evento ainda não resgatado: "id&lt;TAB&gt;fim (s Unix)&lt;TAB&gt;pontos&lt;TAB&gt;objetivo"; 204 = nenhum. */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> pending(@RequestParam(required = false) String character) {
        var events = service.pending(character);
        if (events.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(events.stream()
            .map(e -> e.id() + "\t" + e.expiresAtEpoch() + "\t" + e.points() + "\t" + e.objectiveName() + "\n")
            .collect(Collectors.joining()));
    }

    /**
     * Resgate com a senha no corpo (text/plain). 200 = "pontos&lt;TAB&gt;objetivo"; erro = status e o texto
     * que o card mostra (senha inválida, expirado, já resgatado, limite diário...).
     */
    @PostMapping(value = "/{id}/claim", consumes = "text/plain")
    public ResponseEntity<String> claim(@PathVariable long id, @RequestParam(required = false) String character,
                                        @RequestBody(required = false) String password) {
        try {
            var claim = service.claim(id, character, password);
            return ResponseEntity.ok().contentType(TEXT)
                .body(claim.getPoints() + "\t" + claim.getObjective().getName().replaceAll("[\\t\\r\\n]+", " ") + "\n");
        } catch (AppException e) {
            return ResponseEntity.status(e.getStatus()).contentType(TEXT).body(language.text(character, e.getMessage()));
        }
    }
}
