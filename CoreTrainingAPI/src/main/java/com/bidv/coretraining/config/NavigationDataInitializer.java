package com.bidv.coretraining.config;

import com.bidv.coretraining.entity.NavigationItem;
import com.bidv.coretraining.repository.NavigationItemRepository;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NavigationDataInitializer implements ApplicationRunner {
        private final NavigationItemRepository repository;

        public NavigationDataInitializer(NavigationItemRepository repository) {
                this.repository = repository;
        }

        @Override
        public void run(ApplicationArguments args) {
                seed("dashboard", "Tổng quan", "/", "Home", "MAIN", 10, true, "ROLE_USER", "ROLE_ADMIN");
                seed("leaderboard", "Bảng vinh danh", "/leaderboard", "Trophy", "MAIN", 20, true, "ROLE_USER",
                                "ROLE_ADMIN");
                seed("flashcards", "Flashcard", "/flashcards", "Layers3", "MAIN", 25, true, "ROLE_USER", "ROLE_ADMIN");
                seed("profile", "Hồ sơ", "/profile", "UserCircle", "MAIN", 30, false, "ROLE_USER", "ROLE_ADMIN");
                seed("lesson", "Bài học", "/lesson", "BookOpen", "MAIN", 40, false, "ROLE_USER", "ROLE_ADMIN");
                seed("quiz", "Bài kiểm tra", "/quiz", "HelpCircle", "MAIN", 50, false, "ROLE_USER", "ROLE_ADMIN");
                seed("course.outline", "Nội dung khóa học", "/courses/:courseId", "BookOpen", "MAIN", 45, false,
                                "ROLE_USER",
                                "ROLE_ADMIN");
                seed("admin", "Quản trị", "/admin", "Settings", "MAIN", 60, true, "ROLE_ADMIN");
                seed("admin.overview", "Tổng quan", "/admin", "Settings", "ADMIN", 100, true, "ROLE_ADMIN");
                seed("admin.access", "Đơn vị và phân quyền", "/admin/access", "Users", "ADMIN", 108, false,
                                "ROLE_ADMIN");
                seed("admin.users", "Tài khoản", "/admin/users", "Users", "ADMIN", 110, true, "ROLE_ADMIN");
                seed("admin.units", "Đơn vị / phòng", "/admin/units", "Building2", "ADMIN", 111, true, "ROLE_ADMIN");
                seed("admin.permissions", "Phân quyền", "/admin/permissions", "ShieldCheck", "ADMIN", 114, true,
                                "ROLE_ADMIN");
                seed("admin.enrollments", "Duyệt đăng ký", "/admin?tab=enrollments", "ClipboardCheck", "ADMIN", 120,
                                true,
                                "ROLE_ADMIN");
                seed("admin.curriculum", "Cấu trúc đào tạo (7 Cấp)", "/admin/curriculum", "Layers", "ADMIN", 104, true, "ROLE_ADMIN");
                seed("admin.catalog", "Danh mục đào tạo", "/admin/catalog", "BookOpen", "ADMIN", 105, true,
                                "ROLE_ADMIN");
                seed("admin.courses", "Khóa học", "/admin/courses", "BookOpen", "ADMIN", 106, true, "ROLE_ADMIN");
                seed("admin.lessons", "Bài học", "/admin/courses", "BookOpen", "ADMIN", 130, false, "ROLE_ADMIN");
                seed("admin.lesson.course", "Quản lý bài học theo khóa học", "/admin/lessons/:courseId", "BookOpen",
                                "ADMIN",
                                135, false, "ROLE_ADMIN");
                seed("admin.exams", "Đề thi trắc nghiệm", "/admin?tab=exams", "HelpCircle", "ADMIN", 140, true,
                                "ROLE_ADMIN");
                seed("admin.documents", "Tài liệu AI", "/admin?tab=documents", "FileText", "ADMIN", 145, true,
                                "ROLE_ADMIN");
                seed("admin.quiz.create", "Tạo bài kiểm tra", "/admin/quizzes/new", "Plus", "ADMIN", 150, false,
                                "ROLE_ADMIN");
                seed("admin.quiz.lesson", "Chi tiết kiểm tra bài học", "/admin/quizzes/lesson/:scopeId", "HelpCircle",
                                "ADMIN",
                                160, false, "ROLE_ADMIN");
                seed("admin.quiz.course", "Chi tiết kiểm tra khóa học", "/admin/quizzes/course/:scopeId", "HelpCircle",
                                "ADMIN",
                                170, false, "ROLE_ADMIN");
        }

        private void seed(String id, String label, String route, String icon, String area,
                        int order, boolean visible, String... roles) {
                if (repository.existsById(id)) {
                        if (Set.of("admin.access", "admin.users", "admin.units", "admin.permissions")
                                        .contains(id)) {
                                var item = repository.findById(id).orElseThrow();
                                item.setLabel(label);
                                item.setIcon(icon);
                                item.setRoute(route);
                                item.setSortOrder(order);
                                item.setVisible(visible);
                                repository.save(item);
                        }
                        return;
                }
                repository.save(NavigationItem.builder().id(id).label(label).route(route).icon(icon)
                                .area(area).sortOrder(order).visible(visible)
                                .allowedRoles(Set.of(roles)).build());
        }
}
