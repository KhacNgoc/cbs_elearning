package com.bidv.coretraining.service;

import com.bidv.coretraining.entity.*;
import java.util.List;

/** Learning requirements exclude supporting resources, irrespective of file format. */
public final class LessonContentPolicy {
    private LessonContentPolicy() {}

    public static boolean hasText(Lesson lesson) {
        return normalizeText(lesson.getTextBody()) != null;
    }

    public static String normalizeText(String value) {
        if (value == null) return null;
        String visible = org.springframework.web.util.HtmlUtils.htmlUnescape(
                value.replaceAll("(?s)<[^>]*>", "")).replace('\u00a0', ' ');
        return visible.isBlank() ? null : value;
    }

    public static boolean hasVideo(Lesson lesson) {
        return lesson.getVideoUrl() != null && !lesson.getVideoUrl().isBlank();
    }

    public static List<LessonDocument> slides(Lesson lesson) {
        return lesson.getDocuments().stream().filter(d -> d.getPurpose() == DocumentPurpose.SLIDE).toList();
    }

    public static boolean hasContent(Lesson lesson) {
        return hasText(lesson) || hasVideo(lesson) || !slides(lesson).isEmpty();
    }

    public static boolean complete(Lesson lesson, LessonProgress progress) {
        return complete(hasText(lesson), hasVideo(lesson), slides(lesson).stream().map(LessonDocument::getId).toList(), progress);
    }

    public static boolean complete(boolean hasText, boolean hasVideo, List<Long> slideIds, LessonProgress progress) {
        if (progress == null || !(hasText || hasVideo || !slideIds.isEmpty())) return false;
        return (!hasText || progress.isTextCompleted())
                && (!hasVideo || progress.getDurationSeconds() != null
                    && Double.isFinite(progress.getDurationSeconds()) && progress.getDurationSeconds() > 0
                    && progress.getWatchedSeconds() != null && Double.isFinite(progress.getWatchedSeconds())
                    && progress.getWatchedSeconds() >= progress.getDurationSeconds() * 0.9)
                && slideIds.stream().allMatch(id -> id != null && progress.getDocumentPositions() != null
                    && progress.getDocumentPositions().get(id.toString()) != null
                    && Boolean.TRUE.equals(progress.getDocumentPositions().get(id.toString()).completed()));
    }

    public static void requirePublishable(Lesson lesson) {
        if (!hasContent(lesson))
            throw new com.bidv.coretraining.exception.BadRequestException("Bài " + lesson.getTitle()
                    + " cần nội dung văn bản, video hoặc slide; tài liệu đính kèm chỉ là tài liệu phụ trợ");
    }
}
