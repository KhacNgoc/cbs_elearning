package com.bidv.coretraining.lesson.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "procedure_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcedureSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "procedure_steps", joinColumns = @JoinColumn(name = "section_id"))
    @Column(name = "step_text", columnDefinition = "TEXT")
    @OrderColumn(name = "step_order")
    @Builder.Default
    private List<String> steps = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "section_order")
    private Integer sectionOrder;
}
