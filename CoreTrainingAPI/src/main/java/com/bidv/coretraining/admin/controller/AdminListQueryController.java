package com.bidv.coretraining.admin.controller;
import com.bidv.coretraining.access.dto.AccessManagementDto;

import com.bidv.coretraining.admin.dto.AdminListQueryDto.*;
import com.bidv.coretraining.admin.service.AdminListQueryService;
import com.bidv.coretraining.course.dto.EnrollmentRequestDto;
import com.bidv.coretraining.lesson.dto.LessonReportDto;
import com.bidv.coretraining.quiz.dto.BankQuestionDto;
import com.bidv.coretraining.user.dto.UserDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminListQueryController {
    private final AdminListQueryService reads;
    public AdminListQueryController(AdminListQueryService reads) { this.reads = reads; }
    @GetMapping("/question-bank/page")
    public CursorPage<BankQuestionDto> bank(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String difficulty,
            @RequestParam(required=false) String cursor, @RequestParam(defaultValue="10") int size) {
        return reads.bank(query, status, difficulty, cursor, size);
    }
    @GetMapping("/users/page")
    public NumberedPage<UserDto> users(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="") String unitId, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return reads.users(query, unitId, status, page, size);
    }
    @GetMapping("/rbac/roles/page")
    public NumberedPage<RoleSummary> roles(@RequestParam(defaultValue="") String query, @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) { return reads.roles(query, status, page, size, false); }
    @GetMapping("/rbac/role-options/page")
    public NumberedPage<RoleSummary> roleOptions(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) { return reads.roles(query, "", page, size, true); }
    @GetMapping("/rbac/role-options/item/{id}")
    public RoleSummary roleOption(@PathVariable String id) { return reads.roleOption(id); }
    @GetMapping("/rbac/roles/member-options/page")
    public NumberedPage<RoleMemberRow> members(@RequestParam String roleId, @RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) { return reads.roleMembers(roleId, query, page, size); }
    @GetMapping("/enrollment-requests/page")
    public CursorPage<EnrollmentRequestDto> enrollments(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="") String status, @RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="10") int size) { return reads.enrollments(query, status, cursor, size); }
    @GetMapping("/content-reports/page")
    public ModerationPage reports(@RequestParam(defaultValue="") String query,
            @RequestParam(defaultValue="") String status, @RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="10") int size) { return reads.reports(query, status, cursor, size); }
    @GetMapping("/content-reports/{id}")
    public LessonReportDto report(@PathVariable Long id) { return reads.report(id); }
}
