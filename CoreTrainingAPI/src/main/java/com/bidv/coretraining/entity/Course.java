package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    private String id; // e.g. "CIF", "TIEN_GUI"

    @Column(nullable = false)
    private String name;
    @Column(nullable=false, unique=true) private String code;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="program_id", nullable=false)
    private TrainingProgram program;
    @Builder.Default private String status = "DRAFT";
    @Column(columnDefinition="TEXT") private String description;
    private String thumbnail;
    @Version private Long version;
    @OneToMany(mappedBy="course", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("chapterOrder ASC, id ASC") @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

    @Column(name = "icon_name")
    private String iconName;

    @Column(name = "quiz_passing_score", nullable = false, columnDefinition = "INTEGER DEFAULT 80")
    @Builder.Default
    private Integer quizPassingScore = 80;

    @OneToMany(mappedBy = "course")
    @OrderBy("lessonOrder ASC, id ASC")
    @Builder.Default
    private List<Lesson> lessons = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Badge> badges = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CourseDocument> documents = new ArrayList<>();
}
