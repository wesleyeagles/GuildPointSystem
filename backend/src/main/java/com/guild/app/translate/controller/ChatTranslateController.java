package com.guild.app.translate.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.translate.service.ChatTranslateService;
import com.guild.app.translate.service.ChatTranslateService.AccessView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tradutor do chat do jogo. As rotas /game são da DLL (sem login, liberadas no SecurityConfig), pelo nome
 * do personagem; textos em UTF-8, uma mensagem por linha.
 */
@RestController
@RequestMapping("/api/chat-translate")
@RequiredArgsConstructor
public class ChatTranslateController {
    private final ChatTranslateService service;

    public record AccessRequest(@Size(max = 500) List<Long> memberIds) {}

    /** 204 = personagem liberado (a DLL liga o tradutor), 404 = não. */
    @GetMapping("/game/access")
    public ResponseEntity<Void> gameAccess(@RequestParam(required = false) String character) {
        return service.user(character).isPresent() ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Linhas em russo no corpo; devolve a tradução de cada uma na mesma posição (vazia = não traduziu). */
    @PostMapping(value = "/game", consumes = "text/plain", produces = "text/plain;charset=UTF-8")
    public String translate(@RequestParam(required = false) String character, @RequestBody(required = false) String body) {
        return service.translate(character, body);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/access")
    public AccessView access() { return service.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/access")
    public AccessView updateAccess(@Valid @RequestBody AccessRequest body) {
        return service.update(body.memberIds(), SecurityUtils.currentMember());
    }

    public record TermRequest(@Size(max = 200) String source, @Size(max = 200) String translation) {}

    /** Cria ou troca um termo do glossário (Administrador+). */
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/glossary")
    public AccessView saveTerm(@Valid @RequestBody TermRequest body) {
        return service.saveTerm(body.source(), body.translation(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/glossary/{id}")
    public AccessView deleteTerm(@PathVariable Long id) {
        return service.deleteTerm(id, SecurityUtils.currentMember());
    }
}
