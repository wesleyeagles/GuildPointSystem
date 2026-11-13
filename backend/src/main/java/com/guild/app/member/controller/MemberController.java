package com.guild.app.member.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.event.dto.EventClaimResponse;
import com.guild.app.event.dto.ManualEventRequest;
import com.guild.app.event.service.EventService;
import com.guild.app.member.dto.ApprovalRequest;
import com.guild.app.member.dto.ChangePasswordRequest;
import com.guild.app.member.dto.ResetPasswordResponse;
import com.guild.app.member.dto.LinkDiscordRequest;
import com.guild.app.member.dto.MemberResponse;
import com.guild.app.member.dto.EnglishPlayerRequest;
import com.guild.app.member.dto.SellBlockRequest;
import com.guild.app.member.dto.SuspendMemberRequest;
import com.guild.app.member.dto.UpdateProfileRequest;
import com.guild.app.member.dto.UpdateRoleRequest;
import com.guild.app.member.service.MemberService;
import com.guild.app.points.dto.PointsAdjustmentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final EventService eventService;

    @GetMapping("/ranking")
    public List<MemberResponse> ranking() {
        return memberService.getRanking();
    }

    @GetMapping("/pending")
    public List<MemberResponse> pending() {
        return memberService.getPending();
    }

    @GetMapping("/suspended")
    public List<MemberResponse> suspended() {
        return memberService.getSuspended();
    }

    @GetMapping("/{id}")
    public MemberResponse getById(@PathVariable Long id) {
        return memberService.getById(id);
    }

    @GetMapping("/me")
    public MemberResponse me() {
        return memberService.getById(SecurityUtils.currentMember().getId());
    }

    /** The logged-in member's own password. */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request) {
        memberService.changeOwnPassword(SecurityUtils.currentMember(), request.currentPassword(), request.newPassword());
    }

    /** Líder/Administrador: new temporary password for a member who forgot theirs (returned once). */
    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        return new ResetPasswordResponse(memberService.resetPassword(id, SecurityUtils.currentMember()));
    }

    @PutMapping("/{id}/profile")
    public MemberResponse updateProfile(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest request) {
        return memberService.updateProfile(id, request, SecurityUtils.currentMember());
    }

    @PatchMapping("/{id}/approval")
    public MemberResponse approve(@PathVariable Long id, @Valid @RequestBody ApprovalRequest request) {
        return memberService.approve(id, request, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/points")
    public MemberResponse adjustPoints(@PathVariable Long id, @Valid @RequestBody PointsAdjustmentRequest request) {
        return memberService.adjustPoints(id, request, SecurityUtils.currentMember());
    }

    @GetMapping("/{id}/claims")
    public List<EventClaimResponse> memberClaims(@PathVariable Long id) {
        return eventService.listClaimsForMember(id);
    }

    @PostMapping("/{id}/manual-event")
    @ResponseStatus(HttpStatus.CREATED)
    public EventClaimResponse grantManualEvent(@PathVariable Long id, @Valid @RequestBody ManualEventRequest request) {
        return eventService.grantManualToMember(id, request.objectiveId(), SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/promote-leader")
    public MemberResponse promoteToLeader(@PathVariable Long id) {
        return memberService.promoteToLeader(id, SecurityUtils.currentMember());
    }

    /** Líder/Administrador: delete a member's account (history kept, login and personal data wiped). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id) {
        memberService.deleteAccount(id, SecurityUtils.currentMember());
    }

    /** Líder/Administrador: block a member's access (everything kept) until the suspension is lifted. */
    @PostMapping("/{id}/suspend")
    public MemberResponse suspend(@PathVariable Long id, @Valid @RequestBody SuspendMemberRequest request) {
        return memberService.suspend(id, request, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/unsuspend")
    public MemberResponse unsuspend(@PathVariable Long id) {
        return memberService.unsuspend(id, SecurityUtils.currentMember());
    }

    /** Líder/Administrador: block the member's game account for sale until a date, or lift it (null). */
    @PutMapping("/{id}/sell-block")
    public MemberResponse setSellBlock(@PathVariable Long id, @Valid @RequestBody SellBlockRequest request) {
        return memberService.setSellBlock(id, request.until(), SecurityUtils.currentMember());
    }

    /** Líder/Administrador: tag "English Player" (the member's DLL shows everything in English). */
    @PutMapping("/{id}/english-player")
    public MemberResponse setEnglishPlayer(@PathVariable Long id, @RequestBody EnglishPlayerRequest request) {
        return memberService.setEnglishPlayer(id, request.englishPlayer(), SecurityUtils.currentMember());
    }

    /** Líder/Administrador: link or unlink (blank id) the member's Discord account. */
    @PutMapping("/{id}/discord")
    public MemberResponse linkDiscord(@PathVariable Long id, @Valid @RequestBody LinkDiscordRequest request) {
        return memberService.linkDiscord(id, request, SecurityUtils.currentMember());
    }

    @PatchMapping("/{id}/role")
    public MemberResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return memberService.updateRole(id, request, SecurityUtils.currentMember());
    }
}
