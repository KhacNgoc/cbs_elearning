package com.bidv.coretraining.document.service;

import com.bidv.coretraining.document.dto.CourseDocumentDto;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

/** Business operations for DocumentService. */
public interface DocumentService {
    public CourseDocumentDto upload(String courseId, MultipartFile file);

    public List<CourseDocumentDto> list(String courseId);

    public Map<String, Object> listPaged(String courseId, String search, String statusFilter, int page, int size);

    public void delete(String courseId, String documentId);
}
