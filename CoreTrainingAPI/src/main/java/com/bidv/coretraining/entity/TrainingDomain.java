package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "training_domains", indexes = {
    @Index(name = "idx_domain_module_seq", columnList = "business_module, seq_number DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingDomain {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    @Version
    private Long version;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    private String description;
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "business_module")
    private String businessModule;

    @Column(name = "seq_number")
    private Integer seqNumber;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "domain_unit_grants", joinColumns = @JoinColumn(name = "domain_id"))
    @MapKeyColumn(name = "unit_id")
    @Column(name = "grant_type", nullable = false)
    @Builder.Default
    private java.util.Map<String, String> unitGrants = new java.util.HashMap<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "training_domain_units", joinColumns = @JoinColumn(name = "domain_id"), inverseJoinColumns = @JoinColumn(name = "unit_id"))
    @Builder.Default
    private java.util.Set<OrganizationUnit> units = new java.util.HashSet<>();
}
