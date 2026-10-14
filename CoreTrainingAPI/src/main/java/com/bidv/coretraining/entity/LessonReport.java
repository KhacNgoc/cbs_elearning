package com.bidv.coretraining.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;
@Entity @Table(name="lesson_reports") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LessonReport {
    public enum Status { NEW, IN_REVIEW, RESOLVED, DISMISSED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) private Lesson lesson;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) private User reporter;
    @Column(nullable=false,columnDefinition="TEXT") private String content;
    @ElementCollection @CollectionTable(name="lesson_report_images",joinColumns=@JoinColumn(name="report_id")) @Column(name="image_name") @OrderColumn(name="image_order") @Builder.Default private List<String> images=new ArrayList<>();
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Status status;
    @Column(columnDefinition="TEXT") private String resolution;
    @ManyToOne(fetch=FetchType.LAZY) private User reviewer;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
