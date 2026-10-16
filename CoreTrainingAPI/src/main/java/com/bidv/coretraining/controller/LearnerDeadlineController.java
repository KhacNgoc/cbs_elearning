package com.bidv.coretraining.controller;

import com.bidv.coretraining.entity.*;
import com.bidv.coretraining.repository.*;
import com.bidv.coretraining.service.AuthorizationService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.exception.BadRequestException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users/{userId}/course-deadlines")
public class LearnerDeadlineController {
        public record Deadline(String courseId, String courseName, LocalDate dueDate, LocalDate defaultDate,
                        boolean overridden) {
        }

        public record Request(String courseId, LocalDate dueDate) {
        }

        private final UserRepository users;
        private final CourseRepository courses;
        private final AuthorizationService auth;

        public LearnerDeadlineController(UserRepository users, CourseRepository courses, AuthorizationService auth) {
                this.users = users;
                this.courses = courses;
                this.auth = auth;
        }

        private User target(Long id) {
                User u = users.findById(id).orElseThrow(() -> new BadRequestException("Không tìm thấy học viên"));
                auth.user(u);
                return u;
        }

        private boolean enrolled(User user, Course c) {
                return AccessPolicy.visible(user, c) && (AccessPolicy.mandatory(user, c)
                                || user.getRegisteredCourses().stream().anyMatch(x -> x.getId().equals(c.getId()))
                                || user.hasGroupMandatoryCourse(c.getId()));
        }

        @GetMapping
        @Transactional(readOnly = true)
        public List<Deadline> list(@PathVariable Long userId) {
                User u = target(userId);
                return courses.findAll().stream().filter(c -> "PUBLISHED".equals(c.getStatus()) && enrolled(u, c))
                                .map(c -> new Deadline(c.getId(), c.getName(),
                                                u.getCourseDeadlines().getOrDefault(c.getId(),
                                                                c.getCompletionDeadline()),
                                                c.getCompletionDeadline(),
                                                u.getCourseDeadlines().containsKey(c.getId())))
                                .toList();
        }

        @PutMapping
        @Transactional
        public void save(@PathVariable Long userId, @RequestBody Request request) {
                User u = target(userId);
                Course c = courses.findById(request.courseId())
                                .orElseThrow(() -> new BadRequestException("Không tìm thấy khóa học"));
                if (!enrolled(u, c))
                        throw new BadRequestException("Học viên chưa tham gia khóa học này");
                if (request.dueDate() == null)
                        u.getCourseDeadlines().remove(c.getId());
                else
                        u.getCourseDeadlines().put(c.getId(), request.dueDate());
                users.save(u);
        }
}
