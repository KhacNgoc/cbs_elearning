package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons", uniqueConstraints = @UniqueConstraint(columnNames = { "chapter_id", "lesson_order" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {

    @Id
    private String id; // e.g. "CIF01", "LN01"

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "lesson_order", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @Builder.Default
    private Integer lessonOrder = 0;

    @Column(columnDefinition = "TEXT")
    private String hint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;
    @Builder.Default
    private String status = "DRAFT";
    @Version
    private Long version;
    private java.time.LocalDateTime updatedAt;
    @OneToOne(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private LessonContent content;
    @OneToOne(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private Quiz quiz;

    public String getVideoUrl() {
        return content == null ? null : content.getVideoUrl();
    }

    public VideoSource getVideoSource() {
        return content == null ? VideoSource.URL : content.getVideoSource();
    }

    public String getTextBody() {
        return content == null ? null : content.getTextBody();
    }

    public Integer getQuizPassingScore() {
        return quiz == null ? 80 : quiz.getPassingScore();
    }

    public boolean hasQuiz() {
        return quiz != null && !quiz.getQuestions().isEmpty();
    }

    public boolean isQuizRequired() {
        return hasQuiz() && quiz.isRequiredForProgress();
    }

    public void ensureContent() {
        if (content == null)
            content = LessonContent.builder().lesson(this).build();
    }

    public void setVideoUrl(String value) {
        ensureContent();
        content.setVideoUrl(value);
    }

    public void setVideoSource(VideoSource value) {
        ensureContent();
        content.setVideoSource(value);
    }

    public void setTextBody(String value) {
        ensureContent();
        content.setTextBody(value);
    }

    public void setQuizPassingScore(Integer value) {
        ensureQuiz();
        quiz.setPassingScore(value);
    }

    public void ensureQuiz() {
        if (quiz == null)
            quiz = Quiz.builder().lesson(this).title(title).build();
    }

    @OneToMany(mappedBy = "lesson")
    @Builder.Default
    private List<Question> quizQuestions = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("documentOrder ASC")
    @Builder.Default
    private List<LessonDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LessonKnowledgeSource> knowledgeSources = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionOrder ASC")
    @Builder.Default
    private List<ProcedureSection> procedureSteps = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
}
