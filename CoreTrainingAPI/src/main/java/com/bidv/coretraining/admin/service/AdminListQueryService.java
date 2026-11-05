package com.bidv.coretraining.admin.service;

import com.bidv.coretraining.access.dto.AccessManagementDto;
import com.bidv.coretraining.admin.dto.AdminListQueryDto.*;
import com.bidv.coretraining.course.dto.EnrollmentRequestDto;
import com.bidv.coretraining.lesson.dto.LessonReportDto;
import com.bidv.coretraining.quiz.dto.BankQuestionDto;
import com.bidv.coretraining.user.dto.UserDto;
public interface AdminListQueryService {
    CursorPage<BankQuestionDto> bank(String query, String status, String difficulty, String cursor, int size);
    NumberedPage<UserDto> users(String query, String unitId, String status, int page, int size);
    NumberedPage<RoleSummary> roles(String query, String status, int page, int size, boolean managementOnly);
    RoleSummary roleOption(String id);
    NumberedPage<RoleMemberRow> roleMembers(String roleId, String query, int page, int size);
    CursorPage<EnrollmentRequestDto> enrollments(String query, String status, String cursor, int size);
    ModerationPage reports(String query, String status, String cursor, int size);
    LessonReportDto report(Long id);
}
