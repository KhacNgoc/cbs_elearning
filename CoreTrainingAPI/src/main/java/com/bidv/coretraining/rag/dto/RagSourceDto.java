package com.bidv.coretraining.rag.dto;

public record RagSourceDto(
                String documentId,
                String documentName,
                Integer page,
                String section,
                String courseId,
                String courseName) {
}
