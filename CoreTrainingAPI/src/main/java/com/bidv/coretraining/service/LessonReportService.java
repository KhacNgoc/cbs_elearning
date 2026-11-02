package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.dto.LessonReportDto;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;

/** Business operations for LessonReportService. */
public interface LessonReportService {
    public LessonReportDto submit(String lessonId, String content, List<MultipartFile> files);

    public List<LessonReportDto> list();

    public LessonReportDto detail(Long id);

    public LessonReportDto review(Long id, Long version, LessonReport.Status status, String resolution);

    public Path image(Long id, int index);
}
