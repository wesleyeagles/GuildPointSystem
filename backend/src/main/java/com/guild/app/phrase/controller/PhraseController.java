package com.guild.app.phrase.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.phrase.dto.PhraseBoardResponse;
import com.guild.app.phrase.dto.PhraseRequest;
import com.guild.app.phrase.dto.PhraseResponse;
import com.guild.app.phrase.service.PhraseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/phrases")
@RequiredArgsConstructor
public class PhraseController {

    private final PhraseService phraseService;

    @GetMapping
    public PhraseBoardResponse board() {
        return phraseService.board(SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/vote")
    public PhraseBoardResponse vote(@PathVariable Long id) {
        return phraseService.vote(id, SecurityUtils.currentMember());
    }

    @DeleteMapping("/{id}/vote")
    public PhraseBoardResponse unvote(@PathVariable Long id) {
        return phraseService.unvote(id, SecurityUtils.currentMember());
    }

    /** Administrador ou Líder. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PhraseResponse create(@Valid @RequestBody PhraseRequest request) {
        return phraseService.create(request, SecurityUtils.currentMember());
    }

    /** Administrador ou Líder. */
    @PutMapping("/{id}")
    public PhraseResponse update(@PathVariable Long id, @Valid @RequestBody PhraseRequest request) {
        return phraseService.update(id, request);
    }

    /** Administrador ou Líder. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        phraseService.delete(id);
    }
}
