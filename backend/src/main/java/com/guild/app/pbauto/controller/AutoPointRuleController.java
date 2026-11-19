package com.guild.app.pbauto.controller;

import com.guild.app.pbauto.service.AutoPointRuleService;
import com.guild.app.pbauto.service.AutoPointRuleService.MapOption;
import com.guild.app.pbauto.service.AutoPointRuleService.RuleRequest;
import com.guild.app.pbauto.service.AutoPointRuleService.RuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin → Pontos automáticos (Administrador e Líder; o serviço confere o papel). */
@RestController
@RequestMapping("/api/auto-points")
@RequiredArgsConstructor
public class AutoPointRuleController {
    private final AutoPointRuleService service;

    @GetMapping
    public List<RuleResponse> list() { return service.list(); }

    /** Mapas e regiões já vistos pela DLL, para escolher no formulário. */
    @GetMapping("/maps")
    public List<MapOption> maps() { return service.maps(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@RequestBody RuleRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable long id, @RequestBody RuleRequest request) { return service.update(id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { service.delete(id); }
}
