package com.bidv.coretraining.service.impl;

import com.bidv.coretraining.dto.LearnerDashboardDto.CourseChoice;
import com.bidv.coretraining.exception.ResourceNotFoundException;
import com.bidv.coretraining.repository.LearnerDashboardQueryRepository;
import com.bidv.coretraining.repository.UserRepository;
import com.bidv.coretraining.repository.support.LearnerQueryScopeFactory;
import com.bidv.coretraining.service.RagCourseScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class RagCourseScopeServiceImpl implements RagCourseScopeService {
    private final UserRepository userRepository;
    private final LearnerDashboardQueryRepository query;
    private final int maxCourses;

    public RagCourseScopeServiceImpl(UserRepository userRepository, LearnerDashboardQueryRepository query,
            @Value("${app.rag.max-searchable-courses:500}") int maxCourses) {
        this.userRepository = userRepository;
        this.query = query;
        this.maxCourses = Math.max(1, Math.min(maxCourses, 2000));
    }

    /** Same visibility as the learner course picker: assigned/registered courses plus editor access. */
    @Transactional(readOnly = true)
    public List<CourseChoice> searchableCourses(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        // Fetch one extra row only to detect truncation for wide editor scopes.
        List<CourseChoice> courses = query.accessibleCourses(LearnerQueryScopeFactory.from(user), maxCourses + 1);
        if (courses.size() > maxCourses) {
            log.warn("RAG course scope truncated userId={} limit={}", user.getId(), maxCourses);
            return courses.subList(0, maxCourses);
        }
        return courses;
    }
}
