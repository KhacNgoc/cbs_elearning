package com.bidv.coretraining.entity;
import com.bidv.coretraining.enums.QuestionType;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "bank_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankQuestion {
    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public enum Status {
        DRAFT, ACTIVE, ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @Column(nullable = false, unique = true, length = 80)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;
    @ManyToOne(fetch = FetchType.LAZY)
    private Lesson relatedLesson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;
    @ElementCollection
    @CollectionTable(name = "bank_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order")
    @Column(name = "option_text", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> options = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "bank_question_answers", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "answer_index")
    @OrderColumn(name = "answer_order")
    @Builder.Default
    private List<Integer> correctAnswers = new ArrayList<>();
    @Column(columnDefinition = "TEXT")
    private String explanation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    private java.time.LocalDateTime updatedAt;
}
