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

    private boolean allowed(com.bidv.coretraining.entity.NavigationItem item, java.util.Set<String> permissions) {
        if (item.getPermissionPage() != null)
            return permissions.contains(item.getPermissionPage() + ":read");
        if (item.getId().equals("admin"))
            return permissions.stream().anyMatch(p -> p.endsWith(":read"));
        return true;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NavigationItemDto>> getMyNavigation(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var permissions = com.bidv.coretraining.security.AccessPolicy.permissions(userDetails.getUser());
        var items = new java.util.ArrayList<>(repository.findAllByOrderBySortOrderAsc().stream()
                .filter(item -> allowed(item, permissions))
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
        if (permissions.contains("exams:read")
                && items.stream().noneMatch(i -> i.getId().equals("admin.question-bank")))
            items.add(NavigationItemDto.builder().id("admin.question-bank").label("Ngân hàng câu hỏi")
                    .route("/admin/question-bank").icon("Library").area("ADMIN").visible(true).sortOrder(91).build());
        if (permissions.contains("courses:read")
                && items.stream().noneMatch(i -> i.getId().equals("admin.content-reports")))
            items.add(NavigationItemDto.builder().id("admin.content-reports").label("Báo cáo nội dung")
                    .route("/admin/content-reports").icon("Flag").area("ADMIN").visible(true).sortOrder(92).build());
        return ResponseEntity.ok(items);
    }
}
