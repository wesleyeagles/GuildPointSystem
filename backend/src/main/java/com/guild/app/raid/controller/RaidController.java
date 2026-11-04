package com.guild.app.raid.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.common.enums.Role;
import com.guild.app.raid.service.RaidAccessService;
import com.guild.app.raid.service.RaidAccessService.AccessView;
import com.guild.app.raid.service.RaidLiveService;
import com.guild.app.raid.service.RaidLiveService.LiveMember;
import com.guild.app.raid.service.RaidService;
import com.guild.app.raid.service.RaidService.GroupInviteView;
import com.guild.app.raid.service.RaidService.RaidView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/raids")
@RequiredArgsConstructor
public class RaidController {
    private final RaidService service;
    private final RaidLiveService live;
    private final RaidAccessService access;

    public record CreateRaidRequest(@NotNull @Size(max = 60) String name) {}
    /** hidden: null ou true = entram ocultos (padrão); false = veem a raid. */
    public record AddRaidMembersRequest(@NotEmpty @Size(max = 200) List<Long> memberIds, @Min(1) Integer group, Boolean hidden) {}
    public record MoveRaidMemberRequest(@NotNull @Min(1) Integer group) {}
    public record RaidAccessRequest(@NotNull Role minRole, @Size(max = 500) List<Long> viewerIds) {}
    public record RaidHiddenRequest(boolean hidden) {}
    /** mark: 1..8, ou null para tirar a marca. */
    public record RaidMarkRequest(@Min(1) @Max(8) Integer mark) {}

    /** Se o usuário vê a aba Raid; para Administrador+ também o cargo mínimo e a lista de liberados. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/access")
    public AccessView access() { return access.view(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/access")
    public AccessView updateAccess(@Valid @RequestBody RaidAccessRequest body) {
        return access.update(body.minRole(), body.viewerIds(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<RaidView> list() { return service.list(SecurityUtils.currentMember()); }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public RaidView create(@Valid @RequestBody CreateRaidRequest body) {
        return service.create(body.name(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disband(@PathVariable Long id) {
        service.disband(id, SecurityUtils.currentMember());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/members")
    public RaidView add(@PathVariable Long id, @Valid @RequestBody AddRaidMembersRequest body) {
        return service.addMembers(id, body.memberIds(), body.group(), !Boolean.FALSE.equals(body.hidden()), SecurityUtils.currentMember());
    }

    /** Tirar alguém (líder/organizador) ou sair da raid (o próprio membro). */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long id, @PathVariable Long memberId) {
        service.removeMember(id, memberId, SecurityUtils.currentMember());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/members/{memberId}/group")
    public RaidView move(@PathVariable Long id, @PathVariable Long memberId, @Valid @RequestBody MoveRaidMemberRequest body) {
        return service.moveMember(id, memberId, body.group(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/members/{memberId}/hidden")
    public RaidView hidden(@PathVariable Long id, @PathVariable Long memberId, @RequestBody RaidHiddenRequest body) {
        return service.setHidden(id, memberId, body.hidden(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/members/{memberId}/mark")
    public RaidView mark(@PathVariable Long id, @PathVariable Long memberId, @Valid @RequestBody RaidMarkRequest body) {
        return service.setMark(id, memberId, body.mark(), SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/leader/{memberId}")
    public RaidView leader(@PathVariable Long id, @PathVariable Long memberId) {
        return service.transferLeader(id, memberId, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/group-leader/{memberId}")
    public RaidView groupLeader(@PathVariable Long id, @PathVariable Long memberId) {
        return service.setGroupLeader(id, memberId, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/game-invite")
    public List<GroupInviteView> callInGame(@PathVariable Long id) {
        return service.callInGame(id, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/game-invite")
    public List<GroupInviteView> invites(@PathVariable Long id) {
        return service.invites(id, SecurityUtils.currentMember());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/live")
    public List<LiveMember> live(@PathVariable Long id) {
        return live.forRaid(id, SecurityUtils.currentMember());
    }

    /**
     * DLL de cada integrante (público, pelo nome do personagem; liberado no SecurityConfig):
     * envia o próprio status e recebe o da raid inteira. 204 = não está em raid.
     */
    @PostMapping(value = "/game/status", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> gameStatus(@RequestParam(required = false) String character,
                                             @RequestParam(defaultValue = "0") int fx,
                                             @RequestBody(required = false) String body) {
        // fx=1 (DLL 1.7.0+): a resposta traz a 12a coluna, com os buffs/debuffs de cada integrante.
        // fx=2: também a 13a coluna com mapa e posição (bolinhas no mapa do jogo).
        // fx=3: também as bandeiras no mapa (linhas "F" no fim).
        // fx=4 (DLL 1.14.0+): também a seta da raid (linha "T" no fim).
        return live.report(character, body, Math.max(0, Math.min(fx, 4))).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
