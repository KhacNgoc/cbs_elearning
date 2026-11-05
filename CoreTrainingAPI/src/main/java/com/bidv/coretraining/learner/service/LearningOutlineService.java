package com.bidv.coretraining.learner.service;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.repository.projection.LessonOutlineView;
import com.bidv.coretraining.training.dto.TrainingStructureDto;

/** Business operations for LearningOutlineService. */
public interface LearningOutlineService {
    public TrainingStructureDto.OutlineDto outline(String id, String username);

    public CourseDto courseMetadata(Course course, boolean editor, boolean hasCourseQuiz);

    public LessonDto lessonDto(LessonOutlineView row);
}
