package com.bidv.coretraining.access.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "access_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRole {
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    @Version
    private Long version;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    private String description;
    @Builder.Default
    private boolean active = true;
    private boolean protectedRole;
    @Builder.Default
    private boolean learningEnabled = true;
    // Nullable only for existing rows awaiting the one-time classification migration.
    private Boolean learnerRole;

    public boolean isLearnerRole() {
        return Boolean.TRUE.equals(learnerRole);
    }
    // Only legacy migration can create a course-assignment scope. New roles use
    // unit scope.
    private boolean assignedCoursesOnly;
    private boolean legacyOwnerScope;
    private long membershipRevision;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false)
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "access_role_units", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "unit_id", nullable = false)
    @Builder.Default
    private Set<String> unitIds = new HashSet<>();
}
