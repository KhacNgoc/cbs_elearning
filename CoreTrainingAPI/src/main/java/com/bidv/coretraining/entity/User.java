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
    private String role; // e.g. "ROLE_USER", "ROLE_ADMIN"

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

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserBadge> unlockedBadges = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserQuizScore> quizScores = new HashSet<>();
}
