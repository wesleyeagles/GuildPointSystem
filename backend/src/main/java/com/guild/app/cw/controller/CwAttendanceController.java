package com.guild.app.cw.controller;

import com.guild.app.cw.service.CwAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Presença em CW (Administrador+). Os dados vêm do envio de status da DLL (CharacterStatusService). */
@RestController
@RequestMapping("/api/cw-attendance")
@RequiredArgsConstructor
public class CwAttendanceController {
    private final CwAttendanceService service;

    @GetMapping
    public CwAttendanceService.Overview overview(@RequestParam(defaultValue = "7") int days) {
        return service.overview(days);
    }
}
