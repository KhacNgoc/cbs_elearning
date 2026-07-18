package com.bidv.coretraining.entity;

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
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private String icon; // "Award", "Medal", "Shield"
}
