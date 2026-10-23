package com.bidv.coretraining.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.*;

/** Fail-closed registry mapping for every administration endpoint. */
public record AdminOperation(String page, String action) {
    public String permission() {
        return page + ":" + action;
    }

    public static AdminOperation current(String fallbackPage, String fallbackAction) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes a) {
            var op = from(a.getRequest());
            if (op != null)
                return op;
        }
        return new AdminOperation(fallbackPage, fallbackAction);
    }

    public static AdminOperation from(HttpServletRequest request) {
        String p = request.getRequestURI().substring(request.getContextPath().length());
        if (!p.startsWith("/api/admin/"))
            return null;
        String action = switch (request.getMethod()) {
            case "GET", "HEAD" -> "read";
            case "POST" -> "create";
            case "PUT", "PATCH" -> "update";
            case "DELETE" -> "delete";
            default -> "denied";
        };
        String page;
        if (p.equals("/api/admin/reports/training"))
            page = "overview";
        else if (p.startsWith("/api/admin/options/"))
            page = p.substring("/api/admin/options/".length());
        else if (p.startsWith("/api/admin/rbac/roles"))
            page = "roles";
        else if (p.startsWith("/api/admin/rbac/"))
            page = "permissions";
        else if (p.startsWith("/api/admin/users"))
            page = "users";
        else if (p.matches("/api/admin/units/[^/]+/courses"))
            page = "courses";
        else if (p.startsWith("/api/admin/units"))
            page = "units";
        else if (p.startsWith("/api/admin/groups") || p.startsWith("/api/admin/access/groups"))
            page = "groups";
        else if (p.startsWith("/api/admin/enrollment-requests"))
            page = "enrollments";
        else if (p.startsWith("/api/admin/training") || p.startsWith("/api/admin/programs")
                || p.startsWith("/api/admin/unit-allocations"))
            page = "courses";
        else if (p.contains("/documents") && !p.equals("/api/admin/documents/upload"))
            page = "documents";
        else if (p.startsWith("/api/admin/quizzes") || p.startsWith("/api/admin/questions")
                || p.startsWith("/api/admin/question-bank"))
            page = "exams";
        else if (p.startsWith("/api/admin/content-reports"))
            page = "courses";
        else if (p.startsWith("/api/admin/lessons") || p.startsWith("/api/admin/chapters") || p.contains("/chapters")
                || p.contains("/lessons") || p.equals("/api/admin/videos/upload")
                || p.equals("/api/admin/documents/upload"))
            page = "courses";
        else if (p.startsWith("/api/admin/courses"))
            page = "courses";
        else
            return new AdminOperation("unknown", "denied");
        if ("exams".equals(request.getParameter("page")) &&
                (p.matches("/api/admin/lessons/[^/]+/knowledge-sources")
                        || action.equals("read") && p.matches("/api/admin/courses/[^/]+/documents(/[^/]+/outline)?")))
            page = "exams";
        if (p.equals("/api/admin/courses") && action.equals("read") && request.getParameter("page") != null) {
            if (java.util.Set.of("exams", "documents").contains(request.getParameter("page")))
                page = request.getParameter("page");
            else
                return new AdminOperation("unknown", "denied");
        }
        if (p.endsWith("/status"))
            action = "update";
        if (p.equals("/api/admin/courses/images") && request.getParameter("courseId") != null)
            action = "update";
        if (p.endsWith("/users/upload"))
            action = "create";
        if (p.endsWith("/users/template"))
            action = "read";
        return new AdminOperation(page, action);
    }
}
