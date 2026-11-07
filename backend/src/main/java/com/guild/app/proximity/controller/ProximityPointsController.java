package com.guild.app.proximity.controller;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.exception.AppException;
import com.guild.app.proximity.service.ProximityPointsService;
import com.guild.app.proximity.service.ProximityPointsService.AnchorOption;
import com.guild.app.proximity.service.ProximityPointsService.CreateRequest;
import com.guild.app.proximity.service.ProximityPointsService.Status;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Pontos por aproximação: tela da staff (Moderador+) e as duas rotas da DLL (sem login, pelo nome do personagem). */
@RestController
@RequestMapping("/api/proximity-points")
@RequiredArgsConstructor
public class ProximityPointsController {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final ProximityPointsService service;

    @GetMapping("/anchors")
    public List<AnchorOption> anchors() {
        return service.anchors();
    }

    @PostMapping
    public Status create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal MemberPrincipal actor) {
        return service.create(request, actor);
    }

    @GetMapping("/{id}")
    public Status status(@PathVariable long id) {
        return service.status(id);
    }

    /** DLL: "id&lt;TAB&gt;alcance" por pedido aberto em que o personagem é referência; 204 = nenhum. */
    @GetMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> forGame(@RequestParam(required = false) String character) {
        var lines = service.forGame(character);
        if (lines.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok().contentType(TEXT).body(String.join("\n", lines) + "\n");
    }

    /** DLL: quem está perto, "nick&lt;TAB&gt;distância" por linha (text/plain). 204 = recebido. */
    @PostMapping(value = "/game/{id}", consumes = "text/plain")
    public ResponseEntity<String> report(@PathVariable long id, @RequestParam(required = false) String character,
                                         @RequestBody(required = false) String body) {
        try {
            service.report(id, character, body);
            return ResponseEntity.noContent().build();
        } catch (AppException e) {
            return ResponseEntity.status(e.getStatus()).contentType(TEXT).body(e.getMessage());
        }
    }
}
