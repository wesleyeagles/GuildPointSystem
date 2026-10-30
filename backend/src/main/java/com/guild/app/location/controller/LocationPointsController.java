package com.guild.app.location.controller;

import com.guild.app.auth.security.MemberPrincipal;
import com.guild.app.location.dto.LocationPointsDtos.*;
import com.guild.app.location.service.LocationPointsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/location-points")
@RequiredArgsConstructor
public class LocationPointsController {

    private final LocationPointsService service;

    @GetMapping("/locations")
    public List<LocationOption> locations() {
        return service.locations();
    }

    @PostMapping("/preview")
    public List<LocationMatch> preview(@Valid @RequestBody PreviewRequest request) {
        return service.preview(request);
    }

    @PostMapping("/award")
    public AwardResult award(@Valid @RequestBody AwardRequest request,
                             @AuthenticationPrincipal MemberPrincipal actor) {
        return service.award(request, actor);
    }
}
