package com.guild.app.objective.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.objective.dto.ObjectiveRequest;
import com.guild.app.objective.dto.ObjectiveResponse;
import com.guild.app.objective.service.ObjectiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/objectives")
@RequiredArgsConstructor
public class ObjectiveController {

    private final ObjectiveService objectiveService;

    @GetMapping
    public List<ObjectiveResponse> list() {
        return objectiveService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectiveResponse create(@Valid @RequestBody ObjectiveRequest request) {
        return objectiveService.create(request, SecurityUtils.currentMember());
    }

    @PutMapping("/{id}")
    public ObjectiveResponse update(@PathVariable Long id, @Valid @RequestBody ObjectiveRequest request) {
        return objectiveService.update(id, request, SecurityUtils.currentMember());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        objectiveService.delete(id, SecurityUtils.currentMember());
    }
}
