package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons")
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
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(columnDefinition = "TEXT")
    private String hint;

    @Column(name = "video_url")
    private String videoUrl;

    @ElementCollection
    @CollectionTable(name = "lesson_slides", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "slide_content", columnDefinition = "TEXT")
    @OrderColumn(name = "slide_order")
    @Builder.Default
    private List<String> documentSlides = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sectionOrder ASC")
    @Builder.Default
    private List<ProcedureSection> procedureSteps = new ArrayList<>();

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();
}
