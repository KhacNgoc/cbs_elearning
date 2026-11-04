package com.bidv.coretraining.service;

import com.bidv.coretraining.repository.projection.LessonOutlineView;
import com.bidv.coretraining.dto.TrainingStructureDto;
import com.bidv.coretraining.dto.*;

/** Business operations for LearningOutlineService. */
public interface LearningOutlineService {
    public TrainingStructureDto.OutlineDto outline(String id, String username);

    public LessonDto lessonDto(LessonOutlineView row);
}
