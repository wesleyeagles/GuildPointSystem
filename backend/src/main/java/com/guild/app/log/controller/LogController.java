package com.guild.app.log.controller;

import com.guild.app.log.dto.AuditLogResponse;
import com.guild.app.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public Page<AuditLogResponse> list(Pageable pageable) {
        return logService.list(pageable);
    }
}
