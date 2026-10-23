package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.*;
import com.bidv.coretraining.entity.Comment;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.VideoNote;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.CommentRepository;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.CourseRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.VideoNoteRepository;
import com.bidv.coretraining.service.CourseService;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.entity.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.service.AuthorizationService authorization;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.service.LearningAccessService learningAccess;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.repository.QuestionRepository questions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.service.LearningOutlineService learningOutline;

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final VideoNoteRepository videoNoteRepository;
    private final CourseMapper courseMapper;
    private final NotificationService notificationService;

    public CourseServiceImpl(CourseRepository courseRepository, LessonRepository lessonRepository,
            CommentRepository commentRepository, UserRepository userRepository,
            VideoNoteRepository videoNoteRepository, CourseMapper courseMapper,
            NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoNoteRepository = videoNoteRepository;
        this.courseMapper = courseMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDto> getAllCourses() {
        User user = authorization.current();
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, c)
                        || "PUBLISHED".equals(c.getStatus())
                                && com.bidv.coretraining.security.AccessPolicy.visible(user, c))
                .toList();
        // Keep the full editing DTO for editors; learners only need published metadata.
        var learnerCourseIds = courses.stream()
            .filter(c -> !com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, c))
            .map(Course::getId).toList();
        var summaries = learnerCourseIds.isEmpty() ? java.util.Map.<String, List<com.bidv.coretraining.repository.LessonOutlineView>>of()
            : lessonRepository.findOutlines(learnerCourseIds, false).stream()
                .collect(java.util.stream.Collectors.groupingBy(com.bidv.coretraining.repository.LessonOutlineView::getCourseId));
        var quizCourses = learnerCourseIds.isEmpty() ? java.util.Set.<String>of() : questions.findCourseIdsWithQuiz(learnerCourseIds);
        List<CourseDto> result = new java.util.ArrayList<>();
        for (Course course : courses) {
            boolean editor = com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course);
            CourseDto dto = editor ? courseMapper.toCourseDto(course)
                : learningOutline.courseMetadata(course, false, quizCourses.contains(course.getId()));
            if (!editor) dto.setLessons(summaries.getOrDefault(course.getId(), List.of()).stream()
                .map(learningOutline::lessonDto).toList());
            result.add(dto);
        }
        for (int i = 0; i < courses.size(); i++) {
            boolean editor = com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, courses.get(i));
            result.get(i).setCanEdit(editor);
            result.get(i)
                    .setAllowedActions(com.bidv.coretraining.security.AccessPolicy.courseActions(user, courses.get(i)));
            final Course item = courses.get(i);
            result.get(i)
                    .setAdminActions(java.util.stream.Stream.of("courses", "exams", "documents")
                            .collect(java.util.stream.Collectors.toMap(page -> page,
                                    page -> java.util.stream.Stream.of("read", "create", "update", "delete")
                                            .filter(a -> com.bidv.coretraining.security.AccessPolicy.allowsCourse(user,
                                                    page + ":" + a, item))
                                            .collect(java.util.stream.Collectors.toSet()))));
            if (!editor)
                result.get(i).getLessons().forEach(com.bidv.coretraining.service.TrainingStructureService::outlineOnly);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDto getCourseById(String id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = authorization.current();
        boolean editor = com.bidv.coretraining.security.AccessPolicy.canReadCourse(user, course);
        com.bidv.coretraining.security.AccessPolicy.require(editor || "PUBLISHED".equals(course.getStatus())
                && com.bidv.coretraining.security.AccessPolicy.visible(user, course));
        CourseDto result = editor ? courseMapper.toCourseDto(course)
            : learningOutline.courseMetadata(course, false, questions.existsByCourseIdAndLessonIsNull(id));
        if (!editor) result.setLessons(lessonRepository.findOutline(id, false).stream().map(learningOutline::lessonDto).toList());
        result.setCanEdit(editor);
        if (!editor)
            result.getLessons().forEach(com.bidv.coretraining.service.TrainingStructureService::outlineOnly);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDto getLessonById(String id) {
        learningAccess.requireLesson(authorization.current(), id);
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        LessonDto result = courseMapper.toLessonDetailDto(lesson);
        boolean hasQuiz = questions.existsByLessonId(id);
        result.setHasQuiz(hasQuiz);
        result.setQuizRequired(hasQuiz && lesson.getQuiz() != null && lesson.getQuiz().isRequiredForProgress());
        var comments = loadComments(id, 0, 20);
        result.setComments(comments.content());
        result.setCommentCount(comments.totalElements());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPageDto getComments(String id, int page, int size) {
        learningAccess.requireLesson(authorization.current(), id);
        return loadComments(id, page, size);
    }

    private CommentPageDto loadComments(String id, int page, int size) {
        var request = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(50, size)),
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"),
                org.springframework.data.domain.Sort.Order.desc("id")));
        var roots = commentRepository.findByLessonIdAndParentIsNull(id, request);
        java.util.Map<Long, CommentDto> mapped = new java.util.LinkedHashMap<>();
        for (var root : roots) {
            CommentDto dto = courseMapper.toCommentSummaryDto(root);
            dto.setReplies(new java.util.ArrayList<>());
            mapped.put(dto.getId(), dto);
        }
        if (!mapped.isEmpty()) {
            for (var reply : commentRepository.findByParentIdInOrderByCreatedAtAscIdAsc(mapped.keySet())) {
                CommentDto dto = courseMapper.toCommentSummaryDto(reply);
                dto.setReplies(List.of());
                mapped.get(dto.getParentId()).getReplies().add(dto);
            }
        }
        return new CommentPageDto(List.copyOf(mapped.values()), roots.getNumber(), roots.isLast(), roots.getTotalElements());
    }

    @Override
    @Transactional
    public CommentDto addCommentToLesson(String lessonId, CommentRequest commentRequest, String username) {
        requireInteraction(lessonId, username);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

        String avatar = String.valueOf(username.charAt(0)).toUpperCase();

        Comment comment = Comment.builder()
                .username(username)
                .avatar(avatar)
                .rating(null)
                .content(commentRequest.getContent())
                .createdAt(LocalDateTime.now())
                .lesson(lesson)
                .build();

        Comment savedComment = commentRepository.save(comment);
        return courseMapper.toCommentDto(savedComment);
    }

    @Override
    @Transactional
    public CommentDto replyToComment(String lessonId, Long commentId,
            CommentReplyRequest request, String username) {
        requireInteraction(lessonId, username);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        Comment requestedParent = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        if (!requestedParent.getLesson().getId().equals(lessonId)) {
            throw new IllegalArgumentException("Bình luận không thuộc bài học này");
        }

        Comment replyTarget = requestedParent;
        if (request.getReplyToCommentId() != null) {
            replyTarget = commentRepository.findById(request.getReplyToCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comment", "id", request.getReplyToCommentId()));
            if (!replyTarget.getLesson().getId().equals(lessonId)) {
                throw new IllegalArgumentException("Phản hồi không thuộc bài học này");
            }
        }
        Comment root = requestedParent.getParent() == null
                ? requestedParent
                : requestedParent.getParent();

        Comment reply = Comment.builder()
                .username(username)
                .avatar(String.valueOf(username.charAt(0)).toUpperCase())
                .content(request.getContent().trim())
                .createdAt(LocalDateTime.now())
                .lesson(lesson)
                .parent(root)
                .replyToUsername(replyTarget.getUsername())
                .build();
        Comment savedReply = commentRepository.save(reply);
        if (!replyTarget.getUsername().equals(username)) {
            notificationService.create(
                    replyTarget.getUsername(),
                    NotificationType.COMMENT_REPLY,
                    "Có phản hồi mới cho bình luận của bạn",
                    username + " đã phản hồi bình luận của bạn trong bài "
                            + lesson.getTitle(),
                    "/lesson/" + lessonId);
        }
        return courseMapper.toCommentDto(savedReply);
    }

    @Override
    @Transactional
    public void deleteComment(
            String lessonId, Long commentId, String username, boolean isAdmin) {
        requireInteraction(lessonId, username);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        if (!comment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException("Comment", "id", commentId);
        }
        if (!com.bidv.coretraining.security.AccessPolicy.canEdit(authorization.current(),
                comment.getLesson().getCourse()) && !comment.getUsername().equals(username)) {
            throw new AccessDeniedException("Bạn chỉ có thể gỡ bình luận của chính mình");
        }
        commentRepository.delete(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VideoNoteDto> getVideoNotes(String lessonId, String username) {
        requireInteraction(lessonId, username);
        return videoNoteRepository
                .findAllByLessonIdAndUserUsernameOrderByTimestampSecondsAscCreatedAtAsc(lessonId, username)
                .stream()
                .map(this::toVideoNoteDto)
                .toList();
    }

    @Override
    @Transactional
    public VideoNoteDto createVideoNote(String lessonId, VideoNoteRequest request, String username) {
        requireInteraction(lessonId, username);
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        LocalDateTime now = LocalDateTime.now();

        VideoNote note = VideoNote.builder()
                .lesson(lesson)
                .user(user)
                .timestampSeconds(request.getTimestampSeconds())
                .content(request.getContent().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toVideoNoteDto(videoNoteRepository.save(note));
    }

    @Override
    @Transactional
    public VideoNoteDto updateVideoNote(
            String lessonId, Long noteId, VideoNoteRequest request, String username) {
        requireInteraction(lessonId, username);
        VideoNote note = findOwnedVideoNote(lessonId, noteId, username);
        note.setTimestampSeconds(request.getTimestampSeconds());
        note.setContent(request.getContent().trim());
        note.setUpdatedAt(LocalDateTime.now());
        return toVideoNoteDto(videoNoteRepository.save(note));
    }

    @Override
    @Transactional
    public void deleteVideoNote(String lessonId, Long noteId, String username) {
        requireInteraction(lessonId, username);
        videoNoteRepository.delete(findOwnedVideoNote(lessonId, noteId, username));
    }

    private VideoNote findOwnedVideoNote(String lessonId, Long noteId, String username) {
        return videoNoteRepository.findByIdAndLessonIdAndUserUsername(noteId, lessonId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Video note", "id", noteId));
    }

    private void requireInteraction(String lessonId, String username) {
        learningAccess.requireLesson(username, lessonId);
    }

    private VideoNoteDto toVideoNoteDto(VideoNote note) {
        return VideoNoteDto.builder()
                .id(note.getId())
                .lessonId(note.getLesson().getId())
                .timestampSeconds(note.getTimestampSeconds())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
