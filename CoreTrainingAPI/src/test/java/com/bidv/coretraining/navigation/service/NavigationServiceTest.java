package com.bidv.coretraining.navigation.service;

import com.bidv.coretraining.navigation.entity.NavigationItem;
import com.bidv.coretraining.navigation.repository.NavigationItemRepository;
import com.bidv.coretraining.navigation.service.impl.NavigationServiceImpl;
import com.bidv.coretraining.user.entity.User;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationServiceTest {
    @Test void keepsRepositoryOrderAndVisibilityAndAddsMissingAdminShortcuts() {
        var repository = mock(NavigationItemRepository.class);
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                item("home", null, false), item("admin", null, true), item("courses", "courses", true)));
        var service = new NavigationServiceImpl(repository);
        assertThat(service.navigation(User.builder().role("ROLE_ADMIN").build()))
                .extracting("id").containsExactly("home", "admin", "courses", "admin.question-bank", "admin.content-reports");
        assertThat(service.navigation(User.builder().role("ROLE_USER").build()))
                .singleElement().satisfies(item -> {
                    assertThat(item.getId()).isEqualTo("home");
                    assertThat(item.getVisible()).isFalse();
                });
    }

    @Test void avoidsDuplicatingConfiguredShortcuts() {
        var repository = mock(NavigationItemRepository.class);
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                item("admin.question-bank", "exams", true), item("admin.content-reports", "courses", true)));
        assertThat(new NavigationServiceImpl(repository).navigation(User.builder().role("ROLE_ADMIN").build()))
                .extracting("id").containsExactly("admin.question-bank", "admin.content-reports");
    }

    private NavigationItem item(String id, String page, boolean visible) {
        return NavigationItem.builder().id(id).permissionPage(page).label(id).route("/" + id)
                .icon("Home").area("ADMIN").sortOrder(1).visible(visible).build();
    }
}
