package com.bidv.coretraining.leaderboard.entity;

import com.bidv.coretraining.course.entity.Course;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {

    @Id
    private String id; // e.g. "CIF_gold", "TIEN_GUI_bronze"

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type; // "bronze", "silver", "gold"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String icon; // "Award", "Medal", "Shield"
}
