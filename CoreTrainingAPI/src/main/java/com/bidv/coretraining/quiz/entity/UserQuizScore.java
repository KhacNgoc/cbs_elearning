package com.bidv.coretraining.quiz.entity;

import com.bidv.coretraining.course.entity.Course;
import com.bidv.coretraining.lesson.entity.Lesson;
import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "user_quiz_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuizScore {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "course_id", nullable = false)
        private Course course;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "lesson_id")
        private Lesson lesson;

        @Column(nullable = false)
        private Integer score;

        @Column(nullable = false)
        private Integer total;

        @Column(name = "taken_at", nullable = false)
        private LocalDateTime takenAt;

        /** JSON order of questions and options the learner saw in this result; null means the editor's order. */
        @Column(name = "question_layout", columnDefinition = "TEXT")
        private String questionLayout;

        @ElementCollection
        @CollectionTable(name = "user_quiz_score_answers", joinColumns = @JoinColumn(name = "quiz_score_id"))
        @MapKeyColumn(name = "question_id")
        @Column(name = "selected_answer", nullable = false)
        @Builder.Default
        private Map<Long, Integer> selectedAnswers = new HashMap<>();

        @ElementCollection
        @CollectionTable(name = "user_quiz_score_answer_sets", joinColumns = @JoinColumn(name = "quiz_score_id"))
        @MapKeyColumn(name = "question_id")
        @Column(name = "selected_answers", nullable = false)
        @Builder.Default
        private Map<Long, String> selectedAnswerSets = new HashMap<>();
}
