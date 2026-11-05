package com.bidv.coretraining.lesson.service;

import com.bidv.coretraining.document.dto.DocumentOutlineDto;
import com.bidv.coretraining.lesson.dto.LessonKnowledgeSourceDto;
import com.bidv.coretraining.lesson.entity.Lesson;
import java.util.List;
import java.util.Map;

public interface LessonKnowledgeSourceService {
    List<LessonKnowledgeSourceDto> listAuthorized(String lessonId);
    List<LessonKnowledgeSourceDto> saveAuthorized(String lessonId, List<LessonKnowledgeSourceDto> request);
    DocumentOutlineDto outlineAuthorized(String courseId, String documentId);

    // Internal operations retain caller-owned authorization for generation and other use cases.
    List<LessonKnowledgeSourceDto> list(String lessonId);
    List<LessonKnowledgeSourceDto> save(String lessonId, List<LessonKnowledgeSourceDto> request);
    DocumentOutlineDto outline(String courseId, String documentId);
    List<Map<String, Object>> generationSources(Lesson lesson);
}
