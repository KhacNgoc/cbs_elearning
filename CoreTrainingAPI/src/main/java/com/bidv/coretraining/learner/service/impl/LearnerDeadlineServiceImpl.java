package com.bidv.coretraining.learner.service.impl;

import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.*;
import org.springframework.stereotype.Service;
import com.bidv.coretraining.access.service.AuthorizationService;
import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.course.repository.CourseRepository;
import com.bidv.coretraining.exception.BadRequestException;
import com.bidv.coretraining.learner.service.LearnerDeadlineService;
import com.bidv.coretraining.security.AccessPolicy;
import com.bidv.coretraining.user.entity.User;
import com.bidv.coretraining.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class LearnerDeadlineServiceImpl implements LearnerDeadlineService {
        private final UserRepository users;
        private final CourseRepository courses;
        private final AuthorizationService auth;

        public LearnerDeadlineServiceImpl(UserRepository users, CourseRepository courses, AuthorizationService auth) {
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
                                || user.getRegisteredCourses().stream().anyMatch(x -> x.getId().equals(c.getId())));
        }

        @Transactional(readOnly = true)
        public List<Deadline> list(Long userId) {
                User u = target(userId);
                return courses.findByStatus("PUBLISHED").stream().filter(c -> enrolled(u, c))
                                .map(c -> new Deadline(c.getId(), c.getName(),
                                                u.getCourseDeadlines().getOrDefault(c.getId(),
                                                                c.getCompletionDeadline()),
                                                c.getCompletionDeadline(),
                                                u.getCourseDeadlines().containsKey(c.getId())))
                                .toList();
        }

        @Transactional
        public void save(Long userId, Request request) {
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
