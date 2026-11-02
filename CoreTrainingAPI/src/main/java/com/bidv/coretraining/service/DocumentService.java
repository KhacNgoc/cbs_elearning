package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.CourseDocumentDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/** Business operations for DocumentService. */
public interface DocumentService {
    public CourseDocumentDto upload(String courseId, MultipartFile file);

    public List<CourseDocumentDto> list(String courseId);

    public void delete(String courseId, String documentId);
}
