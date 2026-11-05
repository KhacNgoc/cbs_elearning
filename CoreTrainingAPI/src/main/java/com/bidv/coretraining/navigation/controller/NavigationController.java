package com.bidv.coretraining.navigation.controller;

import com.bidv.coretraining.navigation.dto.NavigationItemDto;
import com.bidv.coretraining.navigation.service.NavigationService;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/navigation")
public class NavigationController {
    private final NavigationService service;

    public NavigationController(NavigationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NavigationItemDto>> getMyNavigation(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(service.navigation(userDetails.getUser()));
    }
}
