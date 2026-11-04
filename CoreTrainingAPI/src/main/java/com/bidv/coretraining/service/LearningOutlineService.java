package com.bidv.coretraining.service;

import com.bidv.coretraining.repository.projection.LessonOutlineView;
import com.bidv.coretraining.dto.TrainingStructureDto;
import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.Course;

/** Business operations for LearningOutlineService. */
public interface LearningOutlineService {
    public TrainingStructureDto.OutlineDto outline(String id, String username);

    public CourseDto courseMetadata(Course course, boolean editor, boolean hasCourseQuiz);

    public LessonDto lessonDto(LessonOutlineView row);
}
