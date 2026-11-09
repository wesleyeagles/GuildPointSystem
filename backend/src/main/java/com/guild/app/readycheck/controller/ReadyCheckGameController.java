package com.guild.app.readycheck.controller;

import com.guild.app.common.exception.AppException;
import com.guild.app.readycheck.service.ReadyCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Check de "Pronto?" da PT, todo pela DLL (sem login, liberado no SecurityConfig), pelo nome do personagem.
 * Respostas em text/plain UTF-8 (formato em {@link ReadyCheckService#current}).
 */
@RestController
@RequestMapping("/api/ready-check/game")
@RequiredArgsConstructor
public class ReadyCheckGameController {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final ReadyCheckService service;

    /** Check atual em que o personagem está; 204 = nenhum. */
    @GetMapping(produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> current(@RequestParam(required = false) String character) {
        String view = service.current(character);
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok().contentType(TEXT).body(view);
    }

    /** Líder da PT: um nick por linha, o líder primeiro. */
    @PostMapping(value = "/start", consumes = "text/plain")
    public ResponseEntity<String> start(@RequestParam(required = false) String character,
                                        @RequestBody(required = false) String body) {
        return reply(() -> service.start(character, body));
    }

    /** Integrante: "Y" pronto, "N" não. */
    @PostMapping(value = "/{id}/answer", consumes = "text/plain")
    public ResponseEntity<String> answer(@PathVariable long id, @RequestParam(required = false) String character,
                                         @RequestBody(required = false) String body) {
        return reply(() -> service.answer(id, character, body));
    }

    private static ResponseEntity<String> reply(Supplier<String> action) {
        try {
            return ResponseEntity.ok().contentType(TEXT).body(action.get());
        } catch (AppException e) {
            return ResponseEntity.status(e.getStatus()).contentType(TEXT).body(e.getMessage());
        }
    }
}
