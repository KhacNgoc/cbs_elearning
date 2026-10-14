package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable=false,columnDefinition="bigint default 0") private Long version;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_color")
    private String avatarColor;

    @Column(name = "employee_id")
    private String employeeId;

    @Column
    private String email;

    @Column(nullable = false)
    private String role;

    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="user_roles",joinColumns=@JoinColumn(name="user_id"))
    @Column(name="role",nullable=false) @Builder.Default
    private Set<String> roles = new HashSet<>();

    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="unit_id")
    private OrganizationUnit organizationUnit;

    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="user_scope_units",joinColumns=@JoinColumn(name="user_id"))
    @Column(name="unit_id",nullable=false) @Builder.Default
    private Set<String> scopeUnitIds = new HashSet<>();

    @Column(nullable=false,columnDefinition="varchar(32) default 'ACTIVE'") @Builder.Default
    private String status="ACTIVE";
    @Column(nullable=false,columnDefinition="bigint default 0") @Builder.Default
    private long tokenVersion=0;
    @Column(name="learner_type",columnDefinition="varchar(255) default 'CURRENT_STAFF'") @Builder.Default private String learnerType="CURRENT_STAFF";

    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="user_access_roles",joinColumns=@JoinColumn(name="user_id"),inverseJoinColumns=@JoinColumn(name="role_id"))
    @Builder.Default private Set<AccessRole> assignedRoles=new HashSet<>();
    @Column(nullable=false,columnDefinition="boolean default false") private boolean dynamicAccessInitialized;
    @Transient private java.util.List<com.bidv.coretraining.security.RolePolicy> policySnapshot;

    public Set<String> effectiveRoles() {
        if(dynamicAccessInitialized) return com.bidv.coretraining.security.AccessPolicy.policies(this).stream().filter(com.bidv.coretraining.security.RolePolicy::active).map(com.bidv.coretraining.security.RolePolicy::code).collect(java.util.stream.Collectors.toSet());
        return roles == null || roles.isEmpty() ? (role == null ? Set.of() : Set.of(role)) : Set.copyOf(roles);
    }
    public boolean hasRole(String value) { return effectiveRoles().contains(value); }
    public boolean activeAccount() { return "ACTIVE".equals(status); }
 // e.g. "ROLE_USER", "ROLE_ADMIN"

    @Column(name = "must_change_password", nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private UserGroup userGroup;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_registered_courses",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<Course> registeredCourses = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_completed_lessons",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "lesson_id")
    )
    @Builder.Default
    private Set<Lesson> completedLessons = new HashSet<>();

    @ElementCollection
    @CollectionTable(name="user_completed_courses", joinColumns=@JoinColumn(name="user_id"))
    @MapKeyColumn(name="course_id") @Column(name="completed_at") @Builder.Default
    private java.util.Map<String,java.time.LocalDateTime> completedCourses = new java.util.HashMap<>();
    @ElementCollection
    @CollectionTable(name="user_course_deadlines",joinColumns=@JoinColumn(name="user_id"))
    @MapKeyColumn(name="course_id") @Column(name="due_date") @Builder.Default
    private java.util.Map<String,java.time.LocalDate> courseDeadlines = new java.util.HashMap<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserBadge> unlockedBadges = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserQuizScore> quizScores = new HashSet<>();
}
