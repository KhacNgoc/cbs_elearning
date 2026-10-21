package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courses", indexes = {
    @Index(name = "idx_course_module_seq", columnList = "business_module, seq_number DESC")
})
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
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_unit_id")
    private OrganizationUnit ownerUnit;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "course_unit_grants", joinColumns = @JoinColumn(name = "course_id"))
    @MapKeyColumn(name = "unit_id")
    @Column(name = "grant_type", nullable = false)
    @Builder.Default
    private java.util.Map<String, String> unitGrants = new java.util.HashMap<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "course_instructors", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "user_id", nullable = false)
    @Builder.Default
    private java.util.Set<Long> instructorIds = new java.util.HashSet<>();

    @Column(nullable = false, unique = true)
    private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private TrainingProgram program;
    @Builder.Default
    private String status = "DRAFT";
    @Column(columnDefinition = "TEXT")
    private String description;
    private String thumbnail;
    private String businessModule;
    @Column(name = "seq_number")
    private Integer seqNumber;
    private String level;
    @Column(columnDefinition = "TEXT")
    private String keywords;
    private java.time.LocalDate completionDeadline;
    @Version
    private Long version;
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("chapterOrder ASC, id ASC")
    @Builder.Default
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
