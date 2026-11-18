package com.bidv.coretraining.navigation.service.impl;

import com.bidv.coretraining.navigation.dto.NavigationItemDto;
import com.bidv.coretraining.navigation.repository.NavigationItemRepository;
import com.bidv.coretraining.navigation.service.NavigationService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NavigationServiceImpl implements NavigationService {
    private final NavigationItemRepository repository;

    public NavigationServiceImpl(NavigationItemRepository repository) {
        this.repository = repository;
    }

    private boolean allowed(com.bidv.coretraining.navigation.entity.NavigationItem item, java.util.Set<String> permissions) {
        if (item.getPermissionPage() != null)
            return permissions.contains(item.getPermissionPage() + ":read");
        if (item.getId().equals("admin"))
            return permissions.stream().anyMatch(p -> p.endsWith(":read"));
        return true;
    }

    public List<NavigationItemDto> navigation(com.bidv.coretraining.user.entity.User user) {
        var permissions = com.bidv.coretraining.security.AccessPolicy.permissions(user);
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
        // The report menu is drawn by the client; these entries say which of its views this user may open.
        for (String[] view : com.bidv.coretraining.security.ReportPages.VIEW_PAGE_DEFINITIONS)
            if (permissions.contains(com.bidv.coretraining.security.ReportPages.VIEW_PAGES.get(view[0]) + ":read"))
                items.add(NavigationItemDto.builder().id("admin.reports." + view[0])
                        .label(view[1].substring(view[1].indexOf('·') + 1).trim()).route("/admin?report=" + view[0])
                        .icon("ChartNoAxesCombined").area("ADMIN").visible(false)
                        .sortOrder(100 + Integer.parseInt(view[2])).build());
        return items;
    }
}
