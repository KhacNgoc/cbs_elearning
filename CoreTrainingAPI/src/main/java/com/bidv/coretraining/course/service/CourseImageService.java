package com.bidv.coretraining.course.service;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.*;

/** Business operations for CourseImageService. */
public interface CourseImageService {
    public String upload(MultipartFile file, String courseId, String programId);

    public void assertAttach(String path, String existing);

    public Path read(String name);
}
