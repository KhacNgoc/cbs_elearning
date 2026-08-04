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
        seed("leaderboard", "Bảng vinh danh", "/leaderboard", "Trophy", "MAIN", 20, true, "ROLE_USER", "ROLE_ADMIN");
        seed("profile", "Hồ sơ", "/profile", "UserCircle", "MAIN", 30, false, "ROLE_USER", "ROLE_ADMIN");
        seed("lesson", "Bài học", "/lesson", "BookOpen", "MAIN", 40, false, "ROLE_USER", "ROLE_ADMIN");
        seed("quiz", "Bài kiểm tra", "/quiz", "HelpCircle", "MAIN", 50, false, "ROLE_USER", "ROLE_ADMIN");
        seed("admin", "Quản trị", "/admin", "Settings", "MAIN", 60, true, "ROLE_ADMIN");
        seed("admin.overview", "Tổng quan", "/admin", "Settings", "ADMIN", 100, true, "ROLE_ADMIN");
        seed("admin.users", "Thành viên", "/admin?tab=users", "Users", "ADMIN", 110, true, "ROLE_ADMIN");
        seed("admin.enrollments", "Duyệt đăng ký", "/admin?tab=enrollments", "ClipboardCheck", "ADMIN", 120, true, "ROLE_ADMIN");
        seed("admin.lessons", "Bài học", "/admin?tab=lessons", "BookOpen", "ADMIN", 130, true, "ROLE_ADMIN");
        seed("admin.lesson.topic", "Quản lý bài học theo chủ đề", "/admin/lessons/:topicId", "BookOpen", "ADMIN", 135, false, "ROLE_ADMIN");
        seed("admin.exams", "Đề thi trắc nghiệm", "/admin?tab=exams", "HelpCircle", "ADMIN", 140, true, "ROLE_ADMIN");
        seed("admin.documents", "Tài liệu AI", "/admin?tab=documents", "FileText", "ADMIN", 145, true, "ROLE_ADMIN");
        seed("admin.quiz.create", "Tạo bài kiểm tra", "/admin/quizzes/new", "Plus", "ADMIN", 150, false, "ROLE_ADMIN");
        seed("admin.quiz.lesson", "Chi tiết kiểm tra bài học", "/admin/quizzes/lesson/:scopeId", "HelpCircle", "ADMIN", 160, false, "ROLE_ADMIN");
        seed("admin.quiz.topic", "Chi tiết kiểm tra chủ đề", "/admin/quizzes/topic/:scopeId", "HelpCircle", "ADMIN", 170, false, "ROLE_ADMIN");
    }

    private void seed(String id, String label, String route, String icon, String area,
                      int order, boolean visible, String... roles) {
        if (repository.existsById(id)) return;
        repository.save(NavigationItem.builder().id(id).label(label).route(route).icon(icon)
                .area(area).sortOrder(order).visible(visible)
                .allowedRoles(Set.of(roles)).build());
    }
}
