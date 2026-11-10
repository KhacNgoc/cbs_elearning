package com.bidv.coretraining.policy;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.exception.BadRequestException;

/**
 * Chapters, lessons and lesson content are only edited while the course is a draft. A published course is what
 * learners are studying, so authors move it back to draft first; changing the course status itself is not guarded.
 */
public final class CourseEditPolicy {
    private CourseEditPolicy() {}

    public static void requireDraft(Course course) {
        String status = course.getStatus();
        if ("DELETED".equals(status))
            throw new BadRequestException("Khóa học đã bị xóa");
        if ("PUBLISHED".equals(status))
            throw new BadRequestException(
                    "Khóa học đang xuất bản nên không thể chỉnh sửa nội dung. Hãy chuyển khóa học về bản nháp trước.");
        if ("ARCHIVED".equals(status))
            throw new BadRequestException(
                    "Khóa học đang lưu trữ nên không thể chỉnh sửa nội dung. Hãy chuyển khóa học về bản nháp trước.");
    }
}
