package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.CourseDocument;
import com.bidv.coretraining.entity.DocumentStatus;
import java.time.LocalDateTime;

public record CourseDocumentDto(String id, String courseId, String fileName, String contentType, long sizeBytes,
                                DocumentStatus status, Integer indexedChunks, String errorMessage,
                                LocalDateTime createdAt, LocalDateTime indexedAt) {
    public static CourseDocumentDto from(CourseDocument document) {
        return new CourseDocumentDto(document.getId(), document.getCourse().getId(), document.getFileName(), document.getContentType(),
                document.getSizeBytes(), document.getStatus(), document.getIndexedChunks(), document.getErrorMessage(),
                document.getCreatedAt(), document.getIndexedAt());
    }
}
