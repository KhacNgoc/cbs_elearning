package com.bidv.coretraining.video.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

/** Business operations for VideoStorageService. */
public interface VideoStorageService {
    public void initialize() throws IOException;

    public String store(MultipartFile file);

    public Path getStorageDirectory();
}
