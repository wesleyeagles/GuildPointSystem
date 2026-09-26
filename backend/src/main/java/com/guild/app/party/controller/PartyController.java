package com.guild.app.party.controller;

import com.guild.app.common.enums.PartyMap;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.party.dto.*;
import com.guild.app.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @GetMapping("/board")
    public PartyBoardResponse board(@RequestParam PartyMap map) {
        return partyService.getBoard(map, SecurityUtils.currentMember());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartyBoardResponse create(@Valid @RequestBody CreatePartyRequest request) {
        return partyService.createParty(request, SecurityUtils.currentMember());
    }

    @DeleteMapping("/{id}")
    public PartyBoardResponse disband(@PathVariable Long id) {
        return partyService.disband(id, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/leave")
    public PartyBoardResponse leave(@PathVariable Long id) {
        return partyService.leave(id, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/requests")
    public PartyBoardResponse requestJoin(@PathVariable Long id) {
        return partyService.requestJoin(id, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/invites")
    public PartyBoardResponse invite(@PathVariable Long id, @Valid @RequestBody InviteMemberRequest request) {
        return partyService.invite(id, request, SecurityUtils.currentMember());
    }

    @PostMapping("/pending/{pendingId}/accept")
    public PartyBoardResponse acceptPending(@PathVariable Long pendingId) {
        return partyService.acceptPending(pendingId, SecurityUtils.currentMember());
    }

    @PostMapping("/pending/{pendingId}/reject")
    public PartyBoardResponse rejectPending(@PathVariable Long pendingId) {
        return partyService.rejectPending(pendingId, SecurityUtils.currentMember());
    }

    @DeleteMapping("/pending/{pendingId}")
    public PartyBoardResponse cancelPending(@PathVariable Long pendingId) {
        return partyService.cancelPending(pendingId, SecurityUtils.currentMember());
    }

    @PutMapping("/lfg")
    public PartyBoardResponse upsertLfg(@Valid @RequestBody UpsertLfgRequest request) {
        return partyService.upsertLfg(request, SecurityUtils.currentMember());
    }

    @DeleteMapping("/lfg")
    public PartyBoardResponse leaveLfg(@RequestParam(defaultValue = "GERAL") PartyMap map) {
        return partyService.leaveLfg(SecurityUtils.currentMember(), map);
    }
}
