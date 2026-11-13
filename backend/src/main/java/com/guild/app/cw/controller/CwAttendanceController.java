package com.guild.app.cw.controller;

import com.guild.app.cw.service.CwAttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Presença e pontuação de CW. /game é da DLL (sem login, liberada no SecurityConfig): avisa o fim da CW pelo nome
 * do personagem. O resto é do site (Administrador+). As amostras vêm do envio de status (CharacterStatusService).
 */
@RestController
@RequestMapping("/api/cw-attendance")
@RequiredArgsConstructor
public class CwAttendanceController {
    private final CwAttendanceService service;

    public record EndRequest(@NotNull Instant endedAt) {}

    @GetMapping
    public CwAttendanceService.Overview overview(@RequestParam(defaultValue = "7") int days) {
        return service.overview(days);
    }

    /** A DLL viu "Due to the result of Chip War" no chat. Responde "1" se fechou a CW agora, "0" se não. */
    @PostMapping(value = "/game", produces = "text/plain;charset=UTF-8")
    public String gameEnded(@RequestParam(required = false) String character, @RequestBody(required = false) String body) {
        return service.gameEnded(character, body) ? "1" : "0";
    }

    @PutMapping("/{date}/{slot}/end")
    public CwAttendanceService.Overview end(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @PathVariable int slot, @Valid @RequestBody EndRequest body,
                                            @RequestParam(defaultValue = "7") int days) {
        return service.adminEnd(date, slot, body.endedAt(), days);
    }
}
