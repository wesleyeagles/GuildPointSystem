package com.guild.app.raid.service;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.enums.Role;
import com.guild.app.common.exception.AppException;
import com.guild.app.log.service.AuditLogService;
import com.guild.app.member.entity.Member;
import com.guild.app.member.repository.MemberRepository;
import com.guild.app.websocket.WebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * Quem vê a aba Raid no site: membros aprovados com cargo a partir do mínimo configurado, os liberados
 * pelo nome e, sempre, Administrador e Líder. Só o site depende disso: a DLL de todos continua enviando
 * o status e o painel no jogo aparece para quem está na raid.
 */
@Service
@RequiredArgsConstructor
public class RaidAccessService {
    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final AuditLogService audit;
    private final WebSocketPublisher ws;

    public record Viewer(Long id, String nickname) {}
    /** minRole e viewers só vêm preenchidos para quem pode configurar. */
    public record AccessView(boolean canView, boolean canConfigure, Role minRole, List<Viewer> viewers) {}

    private Role minRole() {
        return Role.valueOf(jdbc.queryForObject("SELECT min_role FROM raid_settings WHERE id=1", String.class));
    }
    private boolean listed(Long memberId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM raid_viewer WHERE member_id=?)", Boolean.class, memberId));
    }
    private static boolean configurer(Member m) { return m.getRole().isAtLeast(Role.ADMINISTRADOR); }

    @Transactional(readOnly = true)
    public boolean canView(Member member) {
        if (!RaidService.eligible(member)) return false;
        return configurer(member) || member.getRole().isAtLeast(minRole()) || listed(member.getId());
    }

    @Transactional(readOnly = true)
    public boolean canView(Long memberId) {
        return members.findById(memberId).map(this::canView).orElse(false);
    }

    /** Para as rotas da raid: 403 para quem não vê a aba. */
    public void requireView(Member member) {
        if (!canView(member)) throw new AppException("Você não tem acesso à Raid.", HttpStatus.FORBIDDEN);
    }

    @Transactional(readOnly = true)
    public AccessView view(MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!configurer(member) || !RaidService.eligible(member)) return new AccessView(canView(member), false, null, List.of());
        return new AccessView(true, true, minRole(), viewers());
    }

    private List<Viewer> viewers() {
        return jdbc.query("SELECT m.id,m.nickname FROM raid_viewer v JOIN member m ON m.id=v.member_id ORDER BY lower(m.nickname)",
            (r, n) -> new Viewer(r.getLong("id"), r.getString("nickname")));
    }

    /** Administrador+: cargo mínimo e a lista inteira de liberados (substitui a anterior). */
    @Transactional
    public AccessView update(Role minRole, List<Long> viewerIds, MemberPrincipal actor) {
        Member member = members.findById(actor.getId()).orElseThrow(() -> new AppException("Membro não encontrado", HttpStatus.NOT_FOUND));
        if (!RaidService.eligible(member) || !configurer(member))
            throw new AppException("Apenas Administradores e o Líder configuram quem vê a Raid.", HttpStatus.FORBIDDEN);
        if (minRole == null) throw new AppException("Escolha o cargo mínimo.", HttpStatus.BAD_REQUEST);
        var ids = new LinkedHashSet<>(viewerIds == null ? List.<Long>of() : viewerIds);
        if (ids.size() > 500) throw new AppException("Lista grande demais.", HttpStatus.BAD_REQUEST);
        for (Long id : ids)
            if (!members.existsById(id)) throw new AppException("Membro não encontrado", HttpStatus.NOT_FOUND);
        jdbc.update("UPDATE raid_settings SET min_role=?,updated_by=?,updated_at=now() WHERE id=1", minRole.name(), member.getId());
        jdbc.update("DELETE FROM raid_viewer");
        for (Long id : ids) jdbc.update("INSERT INTO raid_viewer(member_id,added_by) VALUES(?,?)", id, member.getId());
        var viewers = viewers();
        audit.log(AuditLogType.RAID_ACCESS_UPDATED, member, member,
            Map.of("minRole", minRole.name(), "viewers", viewers.stream().map(Viewer::nickname).toList()));
        ws.publishRaids();
        return new AccessView(true, true, minRole, viewers);
    }
}
