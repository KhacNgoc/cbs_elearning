package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.NavigationItemDto;
import com.bidv.coretraining.repository.NavigationItemRepository;
import com.bidv.coretraining.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/navigation")
public class NavigationController {
    private final NavigationItemRepository repository;

    public NavigationController(NavigationItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NavigationItemDto>> getMyNavigation(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String role = userDetails.getUser().getRole();
        return ResponseEntity.ok(repository.findAllByOrderBySortOrderAsc().stream()
                .filter(item -> item.getAllowedRoles().contains(role))
                .map(item -> NavigationItemDto.builder()
                        .id(item.getId())
                        .label(item.getLabel())
                        .route(item.getRoute())
                        .icon(item.getIcon())
                        .area(item.getArea())
                        .sortOrder(item.getSortOrder())
                        .visible(item.getVisible())
                        .build())
                .toList());
    }
}
