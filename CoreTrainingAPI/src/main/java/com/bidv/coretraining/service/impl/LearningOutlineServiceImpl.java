package com.bidv.coretraining.service.impl;
import com.bidv.coretraining.repository.projection.LessonOutlineView;
import com.bidv.coretraining.policy.LessonContentPolicy;
import com.bidv.coretraining.enums.DocumentPurpose;
import com.bidv.coretraining.dto.LearnerWorkspaceDto;
import com.bidv.coretraining.dto.TrainingStructureDto;

import com.bidv.coretraining.service.*;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One metadata batch for lessons; no detail mapper or per-lesson collection
 * fetches.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningOutlineServiceImpl implements LearningOutlineService {
    private final CourseRepository courses;
    private final UserRepository users;
    private final ChapterRepository chapters;
    private final LessonRepository lessons;
    private final QuestionRepository questions;
    private final LessonProgressRepository progress;
    private final LessonDocumentRepository documents;
    private final UserQuizScoreRepository scores;
    private final LearningAccessService access;

    public TrainingStructureDto.OutlineDto outline(String id, String username) {
        Course course = courses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course"));
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User"));
        boolean editor = access.requireCourse(user, course);
        List<Chapter> chapterList = chapters.findByCourseIdOrderByChapterOrderAscIdAsc(id).stream()
                .filter(ch -> editor || "PUBLISHED".equals(ch.getStatus())).toList();
        List<LessonOutlineView> rows = lessons.findOutline(id, editor);
        Map<String, List<LessonDocumentDto>> documentMetadata = documents.findOutlineDocuments(id, editor).stream()
                .collect(Collectors.groupingBy(LessonDocumentRepository.OutlineDocument::getLessonId,
                        Collectors.mapping(d -> new LessonDocumentDto(d.getId(), d.getName(), d.getDocumentType(),
                                d.getFileUrl(), d.getOriginalFileName(), d.getFileSize(), d.getDocumentOrder(), d.getPurpose()),
                                Collectors.toList())));
        Set<String> completed = users.findCompletedLessonIds(user.getId(), id);
        Map<String, List<LessonOutlineView>> grouped = rows.stream()
                .collect(Collectors.groupingBy(LessonOutlineView::getChapterId));
        Set<String> unlocked = new HashSet<>();
        List<TrainingStructureDto.ChapterDto> result = new ArrayList<>();
        boolean precedingChaptersComplete = true;
        for (Chapter chapter : chapterList) {
            List<LessonOutlineView> all = grouped.getOrDefault(chapter.getId(), List.of());
            List<LessonOutlineView> published = all.stream().filter(l -> "PUBLISHED".equals(l.getStatus())).toList();
            boolean done = !published.isEmpty() && published.stream().allMatch(l -> completed.contains(l.getId()));
            boolean precedingLessonsComplete = true;
            for (LessonOutlineView row : all) {
                if (editor || precedingChaptersComplete && precedingLessonsComplete)
                    unlocked.add(row.getId());
                if ("PUBLISHED".equals(row.getStatus()))
                    precedingLessonsComplete &= completed.contains(row.getId());
            }
            result.add(new TrainingStructureDto.ChapterDto(chapter.getId(), id, chapter.getCode(),
                    chapter.getTitle(),
                    chapter.getDescription(), chapter.getChapterOrder(), chapter.getStatus(), chapter.getVersion(),
                    all.stream().map(row -> {
                        LessonDto dto = lessonDto(row);
                        dto.setDocuments(documentMetadata.getOrDefault(row.getId(), List.of()));
                        return dto;
                    }).toList(), !editor && !precedingChaptersComplete, done));
            if ("PUBLISHED".equals(chapter.getStatus()))
                precedingChaptersComplete &= done;
        }
        Set<String> publishedChapterIds = chapterList.stream().filter(ch -> "PUBLISHED".equals(ch.getStatus()))
                .map(Chapter::getId).collect(Collectors.toSet());
        List<LessonOutlineView> publishedRows = rows.stream().filter(l -> publishedChapterIds.contains(l.getChapterId())
                && "PUBLISHED".equals(l.getStatus())).toList();
        var resume = resume(course, user, publishedRows, completed, unlocked, documentMetadata);
        return new TrainingStructureDto.OutlineDto(courseMetadata(course, editor,
                questions.existsByCourseIdAndLessonIsNull(id)), result,
                !result.isEmpty() && result.stream().allMatch(TrainingStructureDto.ChapterDto::completed),
                resume == null ? null : resume.lessonId(), resume);
    }

    @Override
    public CourseDto courseMetadata(Course course, boolean editor, boolean hasCourseQuiz) {
        var program = course.getProgram();
        return CourseDto.builder().id(course.getId()).code(course.getCode()).name(course.getName())
                .description(course.getDescription()).iconName(course.getIconName()).status(course.getStatus())
                .version(course.getVersion()).thumbnail(course.getThumbnail())
                .businessModule(course.getBusinessModule())
                .level(course.getLevel()).keywords(course.getKeywords())
                .completionDeadline(course.getCompletionDeadline())
                .programId(program.getId()).programName(program.getName()).domainId(program.getDomain().getId())
                .domainName(program.getDomain().getName()).canEdit(editor).hasQuiz(hasCourseQuiz)
                .quizPassingScore(course.getQuizPassingScore()).lessons(List.of()).build();
    }

    public LessonDto lessonDto(LessonOutlineView row) {
        boolean quiz = Boolean.TRUE.equals(row.getHasQuiz());
        return LessonDto.builder().id(row.getId()).title(row.getTitle()).courseId(row.getCourseId())
                .chapterId(row.getChapterId()).status(row.getStatus()).version(row.getVersion())
                .lessonOrder(row.getLessonOrder())
                .hasQuiz(quiz).quizRequired(quiz && Boolean.TRUE.equals(row.getQuizRequired()))
                .hasVideo(Boolean.TRUE.equals(row.getHasVideo()))
                .hasText(Boolean.TRUE.equals(row.getHasText()))
                .quizPassingScore(row.getQuizPassingScore()).documents(List.of()).procedureSteps(List.of())
                .comments(List.of()).build();
    }

    private LearnerWorkspaceDto.Resume resume(Course course, User user, List<LessonOutlineView> rows,
            Set<String> completed, Set<String> unlocked, Map<String, List<LessonDocumentDto>> documentMetadata) {
        Map<String, LessonProgress> history = progress.findByUserIdAndCourseId(user.getId(), course.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLesson().getId(), p -> p));
        var eligible = rows.stream().filter(l -> !completed.contains(l.getId()) && unlocked.contains(l.getId()))
                .toList();
        var selected = eligible.stream().filter(l -> history.containsKey(l.getId())).max(Comparator.comparing(
                l -> history.get(l.getId()).getUpdatedAt(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(eligible.isEmpty() ? rows.stream().findFirst().orElse(null) : eligible.get(0));
        if (selected == null)
            return null;
        var saved = history.get(selected.getId());
        String type = saved == null ? null : saved.getContentType();
        Long documentId = saved == null ? null : saved.getDocumentId();
        boolean requiredQuiz = Boolean.TRUE.equals(selected.getHasQuiz())
                && Boolean.TRUE.equals(selected.getQuizRequired());
        var slideIds = documentMetadata.getOrDefault(selected.getId(), List.of()).stream()
                .filter(d -> d.getPurpose() == DocumentPurpose.SLIDE).map(LessonDocumentDto::getId).toList();
        boolean contentComplete = LessonContentPolicy.complete(Boolean.TRUE.equals(selected.getHasText()),
                Boolean.TRUE.equals(selected.getHasVideo()), slideIds, saved);
        if (contentComplete && requiredQuiz && !passed(user, selected))
            type = "QUIZ";
        else if ("DOCUMENT".equals(type)
                && (documentId == null || !slideIds.contains(documentId))) {
            type = null;
            documentId = null;
        } else if ("VIDEO".equals(type) && !Boolean.TRUE.equals(selected.getHasVideo())
                || "TEXT".equals(type) && !Boolean.TRUE.equals(selected.getHasText()))
            type = null;
        if (type == null) {
            if (Boolean.TRUE.equals(selected.getHasVideo()))
                type = "VIDEO";
            else if (Boolean.TRUE.equals(selected.getHasText())) type = "TEXT";
            else {
                documentId = slideIds.stream().findFirst().orElse(null);
                type = documentId == null ? "TEXT" : "DOCUMENT";
            }
        }
        return new LearnerWorkspaceDto.Resume(course.getId(), selected.getId(), selected.getTitle(),
                course.getName(),
                type, documentId, saved == null || saved.getDocumentPage() == null ? 1 : saved.getDocumentPage(),
                saved == null || saved.getScrollOffset() == null ? 0D : saved.getScrollOffset(),
                saved == null || saved.getLastPositionSeconds() == null ? 0D : saved.getLastPositionSeconds(),
                saved == null ? null : saved.getUpdatedAt());
    }

    private boolean passed(User user, LessonOutlineView lesson) {
        return scores.findByUserIdAndLessonId(user.getId(), lesson.getId())
                .filter(s -> s.getTotal() != null && s.getTotal() > 0)
                .map(s -> Math.round(s.getScore() * 100.0 / s.getTotal()) >= lesson.getQuizPassingScore())
                .orElse(false);
    }
}
