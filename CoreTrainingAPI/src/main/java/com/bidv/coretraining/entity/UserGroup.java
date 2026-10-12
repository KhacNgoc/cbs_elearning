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
    private String name;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="unit_id")
    private OrganizationUnit organizationUnit;
 // e.g. "Giao dịch viên", "Quan hệ khách hàng", "Kiểm soát viên"

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_mandatory_courses",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<Course> mandatoryCourses = new HashSet<>();
}
