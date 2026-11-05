package com.bidv.coretraining.document.service;

import com.bidv.coretraining.document.dto.LessonDocumentDto;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

/** Business operations for DocumentStorageService. */
public interface DocumentStorageService {
    public void initialize() throws IOException;

    public LessonDocumentDto store(MultipartFile file);

    public Path getStorageDirectory();
}
