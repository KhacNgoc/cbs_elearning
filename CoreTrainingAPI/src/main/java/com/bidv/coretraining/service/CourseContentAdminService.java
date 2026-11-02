package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.*;

import java.util.List;

public interface CourseContentAdminService {
    CourseDto createCourse(CourseDto courseDto);

    CourseDto updateCourse(String id, CourseDto courseDto);

    void deleteCourse(String id);

    LessonDto createLesson(LessonDto lessonDto);

    LessonDto updateLesson(String id, LessonDto lessonDto);

    void deleteLesson(String id);

    List<LessonDto> reorderLessons(String courseId, List<String> lessonIds);

    LessonDocumentDto uploadDocument(org.springframework.web.multipart.MultipartFile file);

    UploadResultDto.Video uploadVideo(org.springframework.web.multipart.MultipartFile file);
}
