package com.bidv.coretraining.course.entity;

import com.bidv.coretraining.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="course_ratings",uniqueConstraints=@UniqueConstraint(name="uk_course_rating_user_course",columnNames={"user_id","course_id"}),indexes=@Index(name="ix_course_ratings_course_updated",columnList="course_id,updated_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CourseRating {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="course_id",nullable=false) private Course course;
    @Column(nullable=false) private Integer stars;
    @Column(nullable=false,length=2000) private String content;
    @Column(nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private LocalDateTime updatedAt;
}
