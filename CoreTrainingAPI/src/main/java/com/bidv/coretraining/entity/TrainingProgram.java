package com.bidv.coretraining.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="training_programs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingProgram {
    @Id @Builder.Default private String id = java.util.UUID.randomUUID().toString();
    @Version private Long version;
    @Column(nullable=false, unique=true) private String code;
    @Column(nullable=false) private String name;
    private String description;
    @Builder.Default private String status = "ACTIVE";
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="domain_id", nullable=false)
    private TrainingDomain domain;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="training_program_units",joinColumns=@JoinColumn(name="program_id"),inverseJoinColumns=@JoinColumn(name="unit_id"))
    @Builder.Default private java.util.Set<OrganizationUnit> units=new java.util.HashSet<>();
}
