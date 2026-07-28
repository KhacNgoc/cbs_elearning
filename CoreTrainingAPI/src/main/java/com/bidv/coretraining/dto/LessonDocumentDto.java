package com.bidv.coretraining.dto;

import com.bidv.coretraining.entity.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDocumentDto {
    private Long id;
    private String name;
    private DocumentType documentType;
    private String fileUrl;
    private String originalFileName;
    private Long fileSize;
    private Integer documentOrder;
}
