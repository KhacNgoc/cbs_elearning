package com.bidv.coretraining.mapper;
import com.bidv.coretraining.lesson.dto.LessonDto;

import java.util.List;

/** Removes content bodies from lesson outline responses. */
public final class LessonOutlineMapper {
    private LessonOutlineMapper() {}
    public static void outlineOnly(LessonDto dto) {
        dto.setVideoUrl(null);
        dto.setTextBody(null);
        dto.setDocuments(List.of());
        dto.setProcedureSteps(List.of());
        dto.setComments(List.of());
    }
}
