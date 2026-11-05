package com.bidv.coretraining.admin.repository;
import com.bidv.coretraining.access.entity.AccessRole;
import com.bidv.coretraining.admin.dto.AdminListQueryDto.*;
import com.bidv.coretraining.course.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.lesson.entity.LessonReport;
import com.bidv.coretraining.quiz.entity.BankQuestion;
import com.bidv.coretraining.user.entity.User;
import java.util.*;
public interface AdminListQueryRepository {
    record Boundary(java.time.LocalDateTime time, long id) {}
    record CoursePart(boolean assigned, boolean owner, Set<String> unitIds) {}
    record Scope(boolean system, Long actorId, Set<String> unitIds, boolean legacy,
                 Set<String> activeUnitIds, List<CoursePart> courseParts) {}
    record Rows<T>(List<T> items, boolean hasMore) {}
    record ModerationRows(Rows<LessonReport> rows, long newCount) {}
    Rows<BankQuestion> bank(String pattern, BankQuestion.Status status, BankQuestion.Difficulty difficulty, Boundary boundary, int size, Scope scope);
    NumberedPage<User> users(String pattern, String unitId, String status, int page, int size, Scope scope);
    NumberedPage<AccessRole> roles(String pattern, String status, int page, int size, boolean managementOnly, List<String> allowed);
    List<AccessRole> allRoles();
    AccessRole role(String id);
    LessonReport report(Long id);
    Map<String,Long> roleCounts(List<String> ids);
    NumberedPage<User> roleMembers(String pattern, int page, int size, Scope scope);
    Rows<CourseEnrollmentRequest> enrollments(String pattern, String status, Boundary boundary, int size, Scope scope);
    ModerationRows reports(String pattern, LessonReport.Status status, Boundary boundary, int size, Scope scope);
}
