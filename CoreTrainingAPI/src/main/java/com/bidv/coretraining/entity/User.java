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

    @Column(name = "employee_id", unique = true)
    private String employeeId;

    @Column(unique = true)
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
        name = "user_registered_topics",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    @Builder.Default
    private Set<Topic> registeredTopics = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_completed_lessons",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "lesson_id")
    )
    @Builder.Default
    private Set<Lesson> completedLessons = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserBadge> unlockedBadges = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserQuizScore> quizScores = new HashSet<>();
}
