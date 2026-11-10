package com.guild.app.log.controller;

import com.guild.app.common.enums.AuditLogType;
import com.guild.app.common.security.SecurityUtils;
import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.dto.LogFilter;
import com.guild.app.log.dto.RecentPointsEntry;
import com.guild.app.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) AuditLogType type,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String objective,
            Pageable pageable) {
        return logService.list(new LogFilter(type, memberId, objective), pageable);
    }

    /** Nomes de evento para o filtro "Evento". */
    @GetMapping("/objectives")
    public List<String> objectives() {
        return logService.objectiveNames();
    }

    /** Dashboard: últimas rodadas de pontos (eventos, RG, CW), sem nome de jogador; marca as do membro logado. */
    @GetMapping("/recent-points")
    public List<RecentPointsEntry> recentPoints(@RequestParam(defaultValue = "15") int limit) {
        return logService.recentPoints(limit, SecurityUtils.currentMember().getId());
    }
}
