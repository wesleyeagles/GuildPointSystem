package com.guild.app.objective.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.ObjectiveType;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.objective.dto.ObjectiveRequest;
import com.guild.app.objective.dto.ObjectiveResponse;
import com.guild.app.objective.entity.Objective;
import com.guild.app.objective.repository.ObjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ObjectiveService {

    private final ObjectiveRepository objectiveRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public List<ObjectiveResponse> list() {
        return objectiveRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ObjectiveResponse create(ObjectiveRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        validateLimitado(request);

        var objective = new Objective();
        objective.setName(request.name());
        objective.setPoints(request.points());
        objective.setType(request.type());
        objective.setDailyLimit(request.type() == ObjectiveType.LIMITADO ? request.dailyLimit() : null);
        objective.setCreatedBy(memberRepository.getReferenceById(actor.getId()));
        objective = objectiveRepository.save(objective);

        auditLogService.log(AuditLogType.OBJECTIVE_CREATED,
                memberRepository.getReferenceById(actor.getId()), null, Map.of(
                        "name", objective.getName(),
                        "points", objective.getPoints(),
                        "type", objective.getType().name(),
                        "dailyLimit", objective.getDailyLimit() != null ? objective.getDailyLimit() : 0));

        return toResponse(objective);
    }

    @Transactional
    public ObjectiveResponse update(Long id, ObjectiveRequest request, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        validateLimitado(request);

        var objective = objectiveRepository.findById(id)
                .orElseThrow(() -> new AppException("Objetivo não encontrado.", HttpStatus.NOT_FOUND));
        objective.setName(request.name());
        objective.setPoints(request.points());
        objective.setType(request.type());
        objective.setDailyLimit(request.type() == ObjectiveType.LIMITADO ? request.dailyLimit() : null);
        objective.setUpdatedBy(memberRepository.getReferenceById(actor.getId()));
        objective = objectiveRepository.save(objective);

        auditLogService.log(AuditLogType.OBJECTIVE_UPDATED,
                memberRepository.getReferenceById(actor.getId()), null, Map.of("id", id, "name", objective.getName()));

        return toResponse(objective);
    }

    @Transactional
    public void delete(Long id, MemberPrincipal actor) {
        SecurityUtils.requireRole(Role.ADMINISTRADOR);
        var objective = objectiveRepository.findById(id)
                .orElseThrow(() -> new AppException("Objetivo não encontrado.", HttpStatus.NOT_FOUND));
        objective.setDeleted(true);
        objectiveRepository.save(objective);
        auditLogService.log(AuditLogType.OBJECTIVE_DELETED,
                memberRepository.getReferenceById(actor.getId()), null, Map.of("id", id, "name", objective.getName()));
    }

    private void validateLimitado(ObjectiveRequest request) {
        if (request.type() == ObjectiveType.LIMITADO
                && (request.dailyLimit() == null || request.dailyLimit() < 1)) {
            throw new AppException("Objetivos limitados precisam de um limite diário.", HttpStatus.BAD_REQUEST);
        }
    }

    private ObjectiveResponse toResponse(Objective o) {
        return new ObjectiveResponse(o.getId(), o.getName(), o.getPoints(), o.getType(), o.getDailyLimit(), o.getCreatedAt());
    }
}
