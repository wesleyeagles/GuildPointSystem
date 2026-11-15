package com.guild.app.memevideo.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.memevideo.service.MemeVideoService;
import com.guild.app.memevideo.service.MemeVideoService.NewVideo;
import com.guild.app.memevideo.service.MemeVideoService.View;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Vídeo meme no jogo. O site (Admin/Líder) manda; a DLL do membro busca pelo nome do personagem (sem login,
 * liberado no SecurityConfig) e abre o vídeo numa janela no meio do jogo.
 */
@RestController
@RequestMapping("/api/meme-videos")
@RequiredArgsConstructor
public class MemeVideoController {
    private final MemeVideoService service;

    /** "id TAB id do YouTube TAB início (s)" do vídeo a abrir agora; 204 = nenhum. */
    @GetMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> game(@RequestParam(required = false) String character) {
        return service.next(character)
            .map(v -> ResponseEntity.ok(v.id() + "\t" + v.youtubeId() + "\t" + v.startSecond() + "\n"))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** A DLL abriu o vídeo. 204 = ok; 404 = não era deste personagem ou já tinha sido aberto. */
    @PostMapping("/game/{id}/played")
    public ResponseEntity<Void> played(@PathVariable long id, @RequestParam(required = false) String character) {
        return service.played(id, character) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public View view() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public View send(@RequestBody NewVideo body) { return service.send(body, SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/opt-out")
    public Map<String, Boolean> optOut() { return Map.of("optOut", service.optOut(SecurityUtils.currentMember())); }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/opt-out")
    public Map<String, Boolean> setOptOut(@RequestBody Map<String, Boolean> body) {
        return Map.of("optOut", service.setOptOut(SecurityUtils.currentMember(), Boolean.TRUE.equals(body.get("optOut"))));
    }
}
