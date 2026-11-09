package com.bidv.coretraining.course.service.impl;

import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.learner.service.LearningAccessService;
import com.bidv.coretraining.learner.service.LearningOutlineService;
import com.bidv.coretraining.lesson.repository.projection.LessonOutlineView;
import com.bidv.coretraining.mapper.CourseMapper;
import com.bidv.coretraining.mapper.LessonOutlineMapper;
import com.bidv.coretraining.quiz.repository.QuestionRepository;
import com.bidv.coretraining.security.AccessPolicy;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.course.service.CourseService;
import com.bidv.coretraining.enums.NotificationType;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.lesson.dto.CommentDto;
import com.bidv.coretraining.lesson.dto.CommentPageDto;
import com.bidv.coretraining.lesson.dto.CommentReplyPageDto;
import com.bidv.coretraining.lesson.dto.CommentReplyRequest;
import com.bidv.coretraining.lesson.dto.CommentRequest;
import com.bidv.coretraining.lesson.dto.LessonDto;
import com.bidv.coretraining.lesson.entity.Comment;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.lesson.repository.CommentRepository;
import com.bidv.coretraining.lesson.repository.LessonRepository;
import com.bidv.coretraining.notification.service.NotificationService;
import com.bidv.coretraining.policy.LearningPolicy;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import com.bidv.coretraining.video.dto.VideoNoteDto;
import com.bidv.coretraining.video.dto.VideoNoteRequest;
import com.bidv.coretraining.video.entity.VideoNote;
import com.bidv.coretraining.video.repository.VideoNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {
    private static final int REPLY_PAGE_SIZE = 5;
    private final AuthorizationService authorization;
    private final LearningAccessService learningAccess;
    private final QuestionRepository questions;
    private final LearningOutlineService learningOutline;

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
            NotificationService notificationService,
            AuthorizationService authorization, LearningAccessService learningAccess, QuestionRepository questions, LearningOutlineService learningOutline) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.videoNoteRepository = videoNoteRepository;
        this.courseMapper = courseMapper;
        this.notificationService = notificationService;
        this.authorization = authorization;
        this.learningAccess = learningAccess;
        this.questions = questions;
        this.learningOutline = learningOutline;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDto> getAllCourses() {
        User user = authorization.current();
        List<Course> courses = courseRepository.findAll().stream()
                .filter(c -> AccessPolicy.canReadCourse(user, c)
                        || LearningPolicy.isLearnableStatus(c.getStatus())
                                && AccessPolicy.visible(user, c))
                .toList();
        // Keep the full editing DTO for editors; learners only need published metadata.
        var learnerCourseIds = courses.stream()
            .filter(c -> !AccessPolicy.canReadCourse(user, c))
            .map(Course::getId).toList();
        var summaries = learnerCourseIds.isEmpty() ? java.util.Map.<String, List<LessonOutlineView>>of()
            : lessonRepository.findOutlines(learnerCourseIds, false).stream()
                .collect(java.util.stream.Collectors.groupingBy(LessonOutlineView::getCourseId));
        var quizCourses = learnerCourseIds.isEmpty() ? java.util.Set.<String>of() : questions.findCourseIdsWithQuiz(learnerCourseIds);
        List<CourseDto> result = new java.util.ArrayList<>();
        for (Course course : courses) {
            boolean editor = AccessPolicy.canReadCourse(user, course);
            CourseDto dto = editor ? courseMapper.toCourseDto(course)
                : learningOutline.courseMetadata(course, false, quizCourses.contains(course.getId()));
            if (!editor) dto.setLessons(summaries.getOrDefault(course.getId(), List.of()).stream()
                .map(learningOutline::lessonDto).toList());
            result.add(dto);
        }
        for (int i = 0; i < courses.size(); i++) {
            boolean editor = AccessPolicy.canReadCourse(user, courses.get(i));
            result.get(i).setCanEdit(editor);
            result.get(i)
                    .setAllowedActions(AccessPolicy.courseActions(user, courses.get(i)));
            final Course item = courses.get(i);
            result.get(i)
                    .setAdminActions(java.util.stream.Stream.of("courses", "exams", "documents")
                            .collect(java.util.stream.Collectors.toMap(page -> page,
                                    page -> java.util.stream.Stream.of("read", "create", "update", "delete")
                                            .filter(a -> AccessPolicy.allowsCourse(user,
                                                    page + ":" + a, item))
                                            .collect(java.util.stream.Collectors.toSet()))));
            if (!editor)
                result.get(i).getLessons().forEach(LessonOutlineMapper::outlineOnly);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDto getCourseById(String id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        User user = authorization.current();
        boolean editor = AccessPolicy.canReadCourse(user, course);
        AccessPolicy.require(editor || LearningPolicy.isLearnableStatus(course.getStatus())
                && AccessPolicy.visible(user, course));
        CourseDto result = editor ? courseMapper.toCourseDto(course)
            : learningOutline.courseMetadata(course, false, questions.existsByCourseIdAndLessonIsNull(id));
        if (!editor) result.setLessons(lessonRepository.findOutline(id, false).stream().map(learningOutline::lessonDto).toList());
        result.setCanEdit(editor);
        if (!editor)
            result.getLessons().forEach(LessonOutlineMapper::outlineOnly);
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
            dto.setRepliesHasMore(false);
            mapped.put(dto.getId(), dto);
        }
        if (!mapped.isEmpty()) {
            for (var reply : commentRepository.findReplyPreviews(id, mapped.keySet(), REPLY_PAGE_SIZE + 1)) {
                var thread = mapped.get(reply.getParent().getId());
                if (thread.getReplies().size() == REPLY_PAGE_SIZE) {
                    thread.setRepliesHasMore(true);
                    continue;
                }
                CommentDto dto = courseMapper.toCommentSummaryDto(reply);
                dto.setReplies(List.of());
                thread.getReplies().add(dto);
                thread.setReplyCursor(encodeReplyCursor(reply.getCreatedAt(), reply.getId()));
            }
            mapped.values().stream().filter(thread -> !Boolean.TRUE.equals(thread.getRepliesHasMore()))
                    .forEach(thread -> thread.setReplyCursor(null));
        }
        return new CommentPageDto(List.copyOf(mapped.values()), roots.getNumber(), roots.isLast(), roots.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public CommentReplyPageDto getReplies(String lessonId, Long rootCommentId, String after) {
        learningAccess.requireLesson(authorization.current(), lessonId);
        Comment root = commentRepository.findById(rootCommentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", rootCommentId));
        if (!root.getLesson().getId().equals(lessonId) || root.getParent() != null)
            throw new ResourceNotFoundException("Comment", "id", rootCommentId);
        var request = org.springframework.data.domain.PageRequest.of(0, REPLY_PAGE_SIZE + 1);
        List<Comment> replies;
        if (after == null) {
            replies = commentRepository.findByLessonIdAndParentIdOrderByCreatedAtAscIdAsc(lessonId, rootCommentId, request);
        } else {
            var cursor = decodeReplyCursor(after);
            replies = commentRepository.findRepliesAfter(lessonId, rootCommentId, cursor.time(), cursor.id(), request);
        }
        boolean hasMore = replies.size() > REPLY_PAGE_SIZE;
        var visible = replies.stream().limit(REPLY_PAGE_SIZE).toList();
        var content = visible.stream().map(reply -> {
            var dto = courseMapper.toCommentSummaryDto(reply);
            dto.setReplies(List.of());
            return dto;
        }).toList();
        String nextCursor = hasMore ? encodeReplyCursor(visible.get(visible.size() - 1).getCreatedAt(),
                visible.get(visible.size() - 1).getId()) : null;
        return new CommentReplyPageDto(content, nextCursor, hasMore);
    }

    private record ReplyCursor(LocalDateTime time, long id) {}

    private String encodeReplyCursor(LocalDateTime time, Long id) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (time + "|" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ReplyCursor decodeReplyCursor(String cursor) {
        try {
            if (cursor.length() > 256) throw new IllegalArgumentException();
            String decoded = new String(java.util.Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf('|');
            var time = LocalDateTime.parse(decoded.substring(0, separator));
            long id = Long.parseLong(decoded.substring(separator + 1));
            if (id <= 0) throw new IllegalArgumentException();
            return new ReplyCursor(time, id);
        } catch (RuntimeException invalid) {
            throw new BadRequestException("Mốc tải phản hồi không hợp lệ");
        }
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
            throw new com.bidv.coretraining.exception.BadRequestException("Bình luận không thuộc bài học này");
        }

        Comment replyTarget = requestedParent;
        if (request.getReplyToCommentId() != null) {
            replyTarget = commentRepository.findById(request.getReplyToCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comment", "id", request.getReplyToCommentId()));
            if (!replyTarget.getLesson().getId().equals(lessonId)) {
                throw new com.bidv.coretraining.exception.BadRequestException("Phản hồi không thuộc bài học này");
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
            String lessonId, Long commentId, String username) {
        requireInteraction(lessonId, username);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", commentId));
        if (!comment.getLesson().getId().equals(lessonId)) {
            throw new ResourceNotFoundException("Comment", "id", commentId);
        }
        if (!AccessPolicy.canEdit(authorization.current(),
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
