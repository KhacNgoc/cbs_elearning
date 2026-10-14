package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="quiz_id", nullable=false) private Quiz quiz;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
    @Column(columnDefinition="TEXT") private String explanation;
    // A snapshot remains usable even when the bank entry is edited or archived.
    private Long bankQuestionId;
    private String bankQuestionCode;

    @ElementCollection
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text", columnDefinition = "TEXT")
    @OrderColumn(name = "option_order")
    @Builder.Default
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_answer", nullable = false)
    private Integer correctAnswer; // 0-indexed index of options

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 32)
    @Builder.Default
    private QuestionType type = QuestionType.SINGLE_CHOICE;

    @ElementCollection
    @CollectionTable(name = "question_correct_answers", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "answer_index", nullable = false)
    @OrderColumn(name = "answer_order")
    @Builder.Default
    private List<Integer> correctAnswers = new ArrayList<>();
}
