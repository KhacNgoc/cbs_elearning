package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.LessonDocumentDto;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

/** Business operations for DocumentStorageService. */
public interface DocumentStorageService {
    public void initialize() throws IOException;

    public LessonDocumentDto store(MultipartFile file);

    public Path getStorageDirectory();
}
