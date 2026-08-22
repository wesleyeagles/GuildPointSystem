package com.guild.app.member.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.MemberStatus;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.dto.ApprovalRequest;
import com.guild.app.member.dto.MemberResponse;
import com.guild.app.member.dto.UpdateProfileRequest;
import com.guild.app.member.entity.Member;
import com.guild.app.member.entity.CharacterClass;
import com.guild.app.member.entity.GameRace;
import com.guild.app.member.repository.CharacterClassRepository;
import com.guild.app.member.repository.GameRaceRepository;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.points.dto.PointsAdjustmentRequest;
import com.guild.app.points.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final GameRaceRepository gameRaceRepository;
    private final CharacterClassRepository characterClassRepository;
    private final AuditLogService auditLogService;
    private final PointsService pointsService;

    @Transactional(readOnly = true)
    public MemberResponse getById(Long id) {
        var member = memberRepository.findById(id)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));
        return toResponse(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getRanking() {
        return memberRepository.findByStatusOrderByPointsDesc(MemberStatus.APROVADO)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getPending() {
        SecurityUtils.requireRole(Role.MODERADOR);
        return memberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.PENDENTE)
                .map(this::toResponse).toList();
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, UpdateProfileRequest request, MemberPrincipal actor) {
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));

        if (!member.getId().equals(actor.getId()) && !actor.getRole().isAtLeast(Role.ADMINISTRADOR)) {
            throw new AppException("Você não tem permissão para esta ação.", HttpStatus.FORBIDDEN);
        }

        var race = gameRaceRepository.findById(request.raceId())
                .orElseThrow(() -> new AppException("Raça inválida.", HttpStatus.BAD_REQUEST));
        var clazz = characterClassRepository.findById(request.classId())
                .orElseThrow(() -> new AppException("Classe inválida.", HttpStatus.BAD_REQUEST));
        validateClassForRace(clazz, race);

        logProfileChange(member, actor, "nickname", member.getNickname(), request.nickname());
        logProfileChange(member, actor, "race", member.getRace() != null ? member.getRace().getName() : null, race.getName());
        logProfileChange(member, actor, "class",
                member.getCharacterClass() != null ? member.getCharacterClass().getName() : null, clazz.getName());

        member.setNickname(request.nickname());
        member.setRace(race);
        member.setCharacterClass(clazz);
        if (request.avatarUrl() != null) {
            member.setAvatarUrl(request.avatarUrl());
        }
        member.setProfileComplete(true);
        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse approve(Long memberId, ApprovalRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.MODERADOR);
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));

        if (request.status() != MemberStatus.APROVADO && request.status() != MemberStatus.REJEITADO) {
            throw new AppException("Status inválido.", HttpStatus.BAD_REQUEST);
        }

        member.setStatus(request.status());
        memberRepository.save(member);

        var type = request.status() == MemberStatus.APROVADO
                ? AuditLogType.MEMBER_APPROVED : AuditLogType.MEMBER_REJECTED;
        Member actorEntity = memberRepository.getReferenceById(actor.getId());
        auditLogService.log(type, actorEntity, member, Map.of("status", request.status().name()));

        return toResponse(member);
    }

    @Transactional
    public MemberResponse adjustPoints(Long memberId, PointsAdjustmentRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));
        var actorEntity = memberRepository.getReferenceById(actor.getId());

        pointsService.adjustPoints(member, actorEntity, request.amount(), request.modality(), request.reason());

        auditLogService.log(AuditLogType.POINTS_ADJUSTED, actorEntity, member, Map.of(
                "amount", request.amount(),
                "modality", request.modality().name(),
                "reason", request.reason()));

        return toResponse(memberRepository.findById(memberId).orElseThrow());
    }

    @Transactional
    public MemberResponse promoteToLeader(Long memberId, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.LIDER);
        if (actor.getRole() != Role.LIDER) {
            throw new AppException("Apenas o líder pode atribuir o cargo de líder.", HttpStatus.FORBIDDEN);
        }

        memberRepository.findByRole(Role.LIDER).ifPresent(existing -> {
            if (!existing.getId().equals(memberId)) {
                existing.setRole(Role.ADMINISTRADOR);
                memberRepository.save(existing);
            }
        });

        var member = memberRepository.findById(memberId)
                .orElseThrow(() -> new AppException("Membro não encontrado.", HttpStatus.NOT_FOUND));
        member.setRole(Role.LIDER);
        return toResponse(memberRepository.save(member));
    }

    private void logProfileChange(Member member, MemberPrincipal actor, String field, String oldVal, String newVal) {
        if (oldVal != null && oldVal.equals(newVal)) return;
        Member actorEntity = memberRepository.getReferenceById(actor.getId());
        auditLogService.log(AuditLogType.PROFILE_UPDATED, actorEntity, member, Map.of(
                "field", field, "oldValue", oldVal != null ? oldVal : "", "newValue", newVal));
    }

    private void validateClassForRace(CharacterClass clazz, GameRace race) {
        if (!clazz.getRace().getId().equals(race.getId())) {
            throw new AppException("A classe não pertence à raça selecionada.", HttpStatus.BAD_REQUEST);
        }
    }

    private MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getAvatarUrl(),
                member.getRace() != null ? member.getRace().getId() : null,
                member.getRace() != null ? member.getRace().getName() : null,
                member.getCharacterClass() != null ? member.getCharacterClass().getId() : null,
                member.getCharacterClass() != null ? member.getCharacterClass().getName() : null,
                member.getRole(),
                member.getStatus(),
                member.getPoints(),
                pointsService.getAvailablePoints(member.getId()),
                member.isProfileComplete());
    }
}
