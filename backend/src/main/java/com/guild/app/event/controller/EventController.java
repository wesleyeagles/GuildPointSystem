package com.guild.app.event.controller;

import com.guild.app.common.security.SecurityUtils;
import com.guild.app.event.dto.ClaimEventRequest;
import com.guild.app.event.dto.CreateEventRequest;
import com.guild.app.event.dto.EventResponse;
import com.guild.app.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public List<EventResponse> listActive() {
        return eventService.listActive(SecurityUtils.currentMember());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest request) {
        return eventService.create(request, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/claim")
    public EventResponse claim(@PathVariable Long id, @Valid @RequestBody ClaimEventRequest request) {
        return eventService.claim(id, request, SecurityUtils.currentMember());
    }

    @PostMapping("/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id) {
        return eventService.cancel(id, SecurityUtils.currentMember());
    }

    @PostMapping("/claims/{claimId}/deny")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void denyClaim(@PathVariable Long claimId) {
        eventService.denyClaim(claimId, SecurityUtils.currentMember());
    }
}
