package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.UserMapper;
import com.bidv.coretraining.dto.UserProfileDto;
import com.bidv.coretraining.dto.UserProgressDto;
import com.bidv.coretraining.entity.Lesson;
import com.bidv.coretraining.entity.User;
import com.bidv.coretraining.entity.Course;
import com.bidv.coretraining.entity.UserQuizScore;
import com.bidv.coretraining.entity.EnrollmentStatus;
import com.bidv.coretraining.entity.CourseEnrollmentRequest;
import com.bidv.coretraining.entity.NotificationType;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LessonRepository;
import com.bidv.coretraining.repository.QuestionRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.UserQuizScoreRepository;
import com.bidv.coretraining.repository.CourseEnrollmentRequestRepository;
import com.bidv.coretraining.service.LearningPolicy;
import com.bidv.coretraining.service.UserService;
import com.bidv.coretraining.service.NotificationService;
import com.bidv.coretraining.service.LessonProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

        private final UserRepository userRepository;
        private final LessonRepository lessonRepository;
        private final QuestionRepository questionRepository;
        private final com.bidv.coretraining.repository.CourseRepository courseRepository;
        private final UserMapper userMapper;
        private final CourseEnrollmentRequestRepository enrollmentRequestRepository;
        private final NotificationService notificationService;
        private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
        private final LessonProgressService lessonProgressService;
        private final UserQuizScoreRepository userQuizScoreRepository;

        public UserServiceImpl(UserRepository userRepository, LessonRepository lessonRepository,
                        QuestionRepository questionRepository,
                        com.bidv.coretraining.repository.CourseRepository courseRepository,
                        UserMapper userMapper,
                        CourseEnrollmentRequestRepository enrollmentRequestRepository,
                        NotificationService notificationService,
                        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                        LessonProgressService lessonProgressService,
                        UserQuizScoreRepository userQuizScoreRepository) {
                this.userRepository = userRepository;
                this.lessonRepository = lessonRepository;
                this.questionRepository = questionRepository;
                this.courseRepository = courseRepository;
                this.userMapper = userMapper;
                this.enrollmentRequestRepository = enrollmentRequestRepository;
                this.notificationService = notificationService;
                this.passwordEncoder = passwordEncoder;
                this.lessonProgressService = lessonProgressService;
                this.userQuizScoreRepository = userQuizScoreRepository;
        }

        @Override
        @Transactional
        public UserProfileDto updateProfile(String username, java.util.Map<String, String> fields) {
                if (!java.util.Set.of("fullName", "email", "avatarColor").containsAll(fields.keySet()))
                        throw new com.bidv.coretraining.exception.BadRequestException(
                                        "Chỉ được cập nhật tên, email và màu đại diện");
                User user = userRepository.findByUsername(username).orElseThrow();
                String name = fields.get("fullName");
                if (name != null) {
                        if (name.isBlank())
                                throw new com.bidv.coretraining.exception.BadRequestException(
                                                "Tên không được để trống");
                        user.setFullName(name.trim());
                }
                String email = fields.get("email");
                if (email != null) {
                        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                                throw new com.bidv.coretraining.exception.BadRequestException("Email không hợp lệ");
                        user.setEmail(email);
                }
                if (fields.get("avatarColor") != null)
                        user.setAvatarColor(fields.get("avatarColor"));
                userRepository.save(user);
                return getUserProfile(username);
        }

        @Override
        @Transactional(readOnly = true)
        public UserProfileDto getUserProfile(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

                return UserProfileDto.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .fullName(user.getFullName())
                                .email(user.getEmail())
                                .employeeId(user.getEmployeeId())
                                .avatarColor(user.getAvatarColor())
                                .role(user.getRole()).roles(user.effectiveRoles())
                                .roleNames(com.bidv.coretraining.security.AccessPolicy.roleNames(user))
                                .systemAdmin(com.bidv.coretraining.security.AccessPolicy.system(user))
                                .permissions(com.bidv.coretraining.security.AccessPolicy.permissions(user))
                                .unitId(user.getOrganizationUnit() == null ? null : user.getOrganizationUnit().getId())
                                .scopeUnitIds(user.getScopeUnitIds()).status(user.getStatus())
                                .learnerType(user.getLearnerType())
                                .unitName(user.getOrganizationUnit() == null ? null
                                                : user.getOrganizationUnit().getName())
                                .points(user.getPoints())
                                .groupIds(user.groupIds())
                                .groupName(user.groupNames())
                                .build();
        }

        @Override
        @Transactional(readOnly = true)
        public UserProgressDto getUserProgress(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
                return buildUserProgressDto(user);
        }

        @Override
        @Transactional
        public UserProgressDto completeLesson(String username, String lessonId) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
                Lesson lesson = lessonRepository.findById(lessonId)
                                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));

                com.bidv.coretraining.service.LearningPolicy.assertLessonAccess(user, lesson);
                lessonProgressService.completeContent(username, lessonId);
                if (lesson.isQuizRequired()
                                && !hasPassedLessonQuiz(user, lesson)) {
                        throw new com.bidv.coretraining.exception.BadRequestException(
                                        "Bạn cần đạt tối thiểu " + lesson.getQuizPassingScore()
                                                        + "% bài kiểm tra của bài học");
                }

                if (!user.getCompletedLessons().contains(lesson)) {
                        user.getCompletedLessons().add(lesson);
                        user.setPoints(user.getPoints() + 100);
                        com.bidv.coretraining.service.LearningPolicy.recordCourseCompletion(user, lesson.getCourse());
                        user = userRepository.save(user);
                }

                return buildUserProgressDto(user);
        }

        @Override
        @Transactional
        public UserProgressDto resetLesson(String username, String lessonId) {
                throw new com.bidv.coretraining.exception.BadRequestException(
                                "Bài đã hoàn thành vẫn có thể học lại; không xóa thành tích đã ghi nhận");
        }

        private boolean hasPassedLessonQuiz(User user, Lesson lesson) {
                return userQuizScoreRepository.findByUserIdAndLessonId(user.getId(), lesson.getId())
                                .filter(score -> score.getTotal() != null && score.getTotal() > 0)
                                .map(score -> Math.round((float) score.getScore() / score.getTotal() * 100) >= lesson
                                                .getQuizPassingScore())
                                .orElse(false);
        }

        private boolean requiresCourseQuizBeforeCourseCompletion(User user, Lesson lesson) {
                com.bidv.coretraining.entity.Course course = lesson.getCourse();
                if (!questionRepository.existsByCourseIdAndLessonIsNull(course.getId())
                                || hasPassedCourseQuiz(user, course)) {
                        return false;
                }
                return course.getLessons().stream()
                                .allMatch(candidate -> candidate.getId().equals(lesson.getId())
                                                || user.getCompletedLessons().stream()
                                                                .anyMatch(completed -> completed.getId()
                                                                                .equals(candidate.getId())));
        }

        private boolean hasPassedCourseQuiz(User user, com.bidv.coretraining.entity.Course course) {
                return userQuizScoreRepository
                                .findByUserIdAndCourseIdAndLessonIsNull(user.getId(), course.getId())
                                .filter(score -> score.getTotal() != null && score.getTotal() > 0)
                                .map(score -> Math.round((float) score.getScore() / score.getTotal() * 100) >= course
                                                .getQuizPassingScore())
                                .orElse(false);
        }

        @Override
        @Transactional
        public UserProgressDto registerCourse(String username, String courseId) {
                User actor = userRepository.findByUsername(username).orElseThrow();
                if (!com.bidv.coretraining.security.AccessPolicy.permissions(actor).contains("LEARN"))
                        throw new org.springframework.security.access.AccessDeniedException(
                                        "Bạn chưa có quyền tham gia học tập nên không thể đăng ký khóa học. Vui lòng liên hệ quản trị viên để được cấp quyền.");
                if (!actor.activeAccount())
                        throw new org.springframework.security.access.AccessDeniedException(
                                        "Tài khoản chưa hoạt động nên không thể đăng ký khóa học.");
                Course target = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
                if (!com.bidv.coretraining.security.AccessPolicy.visible(actor, target))
                        throw new org.springframework.security.access.AccessDeniedException(
                                        "Bạn chưa có quyền truy cập khóa học này. Vui lòng liên hệ quản trị viên.");
                if (!LearningPolicy.isLearnableStatus(target.getStatus()))
                        throw new org.springframework.security.access.AccessDeniedException(
                                        "Khóa học chưa được xuất bản hoặc đã ngừng sử dụng nên không thể đăng ký.");
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
                com.bidv.coretraining.entity.Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

                if (user.getRegisteredCourses().contains(course)
                                || user.hasGroupMandatoryCourse(course.getId())) {
                        return buildUserProgressDto(user);
                }
                if (enrollmentRequestRepository.existsByUserIdAndCourseIdAndStatus(
                                user.getId(), courseId, EnrollmentStatus.PENDING)) {
                        return buildUserProgressDto(user);
                }
                CourseEnrollmentRequest request = CourseEnrollmentRequest.builder()
                                .user(user)
                                .course(course)
                                .status(EnrollmentStatus.PENDING)
                                .requestedAt(java.time.LocalDateTime.now())
                                .build();
                enrollmentRequestRepository.save(request);
                notificationService.createForUnitAdmins(user.getOrganizationUnit(),
                                NotificationType.ENROLLMENT_REQUEST,
                                "Yêu cầu đăng ký khóa học mới",
                                user.getFullName() + " đã yêu cầu đăng ký khóa học " + course.getName(),
                                "/admin?tab=enrollments");

                return buildUserProgressDto(user);
        }

        private UserProgressDto buildUserProgressDto(User user) {
                // Map completed lessons IDs
                java.util.List<String> completedLessons = user.getCompletedLessons().stream()
                                .map(Lesson::getId)
                                .collect(Collectors.toList());

                // Map quiz scores and totals
                Map<String, Integer> quizScores = new HashMap<>();
                Map<String, Integer> quizTotals = new HashMap<>();
                Map<String, Integer> lessonQuizScores = new HashMap<>();
                Map<String, Integer> lessonQuizTotals = new HashMap<>();
                for (UserQuizScore score : user.getQuizScores()) {
                        if (score.getLesson() != null) {
                                String lessonId = score.getLesson().getId();
                                lessonQuizScores.put(lessonId, score.getScore());
                                lessonQuizTotals.put(lessonId, score.getTotal());
                        } else {
                                String courseId = score.getCourse().getId();
                                quizScores.put(courseId, score.getScore());
                                quizTotals.put(courseId, score.getTotal());
                        }
                }

                java.util.List<String> mandatoryCourses = user.getUserGroups().stream()
                                .flatMap(group -> group.getMandatoryCourses().stream())
                                .map(Course::getId).distinct().collect(Collectors.toCollection(ArrayList::new));

                courseRepository.findAll().stream()
                                .filter(c -> LearningPolicy.isLearnableStatus(c.getStatus())
                                                && com.bidv.coretraining.security.AccessPolicy.visible(user, c)
                                                && com.bidv.coretraining.security.AccessPolicy.mandatory(user, c))
                                .map(Course::getId).forEach(mandatoryCourses::add);
                java.util.List<String> registeredCourses = user.getRegisteredCourses().stream()
                                .map(com.bidv.coretraining.entity.Course::getId)
                                .collect(Collectors.toList());
                java.util.List<String> pendingCourses = enrollmentRequestRepository
                                .findByUserIdAndStatus(user.getId(), EnrollmentStatus.PENDING)
                                .stream()
                                .map(request -> request.getCourse().getId())
                                .collect(Collectors.toList());

                return UserProgressDto.builder()
                                .completedLessons(completedLessons)
                                .quizScores(quizScores)
                                .quizTotals(quizTotals)
                                .lessonQuizScores(lessonQuizScores)
                                .lessonQuizTotals(lessonQuizTotals)
                                .lessonProgress(lessonProgressService.getAllProgress(user))
                                .points(user.getPoints())
                                .unlockedBadges(userMapper
                                                .toUserBadgeDtoList(new ArrayList<>(user.getUnlockedBadges())))
                                .groupIds(user.groupIds())
                                .groupName(user.groupNames())
                                .mandatoryCourses(mandatoryCourses.stream().distinct().toList())
                                .registeredCourses(registeredCourses)
                                .pendingCourses(pendingCourses)
                                .mustChangePassword(user.isMustChangePassword())
                                .build();
        }

        @Override
        @Transactional
        public void changePassword(String username, String newPassword) {
                if (newPassword == null || newPassword.length() < 8)
                        throw new com.bidv.coretraining.exception.BadRequestException(
                                        "Mật khẩu phải có tối thiểu 8 ký tự");
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setMustChangePassword(false);
                user.setTokenVersion(user.getTokenVersion() + 1);
                userRepository.save(user);
        }
}
