package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroup {

    @Id
    private String id; // e.g. "GDV", "QHKH", "KSV"

    @Column(nullable = false)
    private String name; // e.g. "Giao dịch viên", "Quan hệ khách hàng", "Kiểm soát viên"

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_mandatory_topics",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    @Builder.Default
    private Set<Topic> mandatoryTopics = new HashSet<>();
}
