package com.bidv.coretraining.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="organization_units") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrganizationUnit {
    @Id private String id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) @Builder.Default private String type="UNIT";
    @ManyToOne(fetch=FetchType.EAGER) private OrganizationUnit parent;
    @Column(nullable=false) @Builder.Default private boolean active=true;
}
