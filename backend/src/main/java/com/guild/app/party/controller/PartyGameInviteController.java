package com.guild.app.party.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.party.service.PartyGameInviteService;
import com.guild.app.party.service.PartyGameInviteService.InviteView;
import com.guild.app.party.service.PartyGameInviteService.PendingInvite;
import com.guild.app.raid.service.RaidService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class PartyGameInviteController {
    private final PartyGameInviteService service;
    private final RaidService raids;

    @PostMapping("/api/parties/{id}/game-invite")
    @PreAuthorize("isAuthenticated()")
    public InviteView start(@PathVariable Long id) { return service.start(id, SecurityUtils.currentMember()); }

    @GetMapping("/api/parties/{id}/game-invite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InviteView> current(@PathVariable Long id) {
        var view = service.current(id, SecurityUtils.currentMember());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    /**
     * DLL do líder: público, pelo nome do personagem (liberado no SecurityConfig). 204 = nada a fazer.
     * Também entrega os convites de subgrupo de raid, no mesmo formato, para a DLL atual chamá-los.
     */
    @GetMapping("/api/party-invites/pending")
    public ResponseEntity<PendingInvite> pending(@RequestParam @NotBlank @Size(max = 32) String character) {
        return service.pending(character).or(() -> raids.pending(character)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/party-invites/{id}/done")
    public ResponseEntity<Void> done(@PathVariable UUID id, @RequestParam @NotBlank @Size(max = 32) String character) {
        return service.done(id, character) || raids.done(id, character) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
