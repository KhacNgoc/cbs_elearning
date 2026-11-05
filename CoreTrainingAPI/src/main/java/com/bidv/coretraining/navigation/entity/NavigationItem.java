package com.bidv.coretraining.navigation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "navigation_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavigationItem {
    @Id
    private String id;
    private String permissionPage;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String route;

    @Column(nullable = false)
    private String icon;

    @Column(nullable = false, length = 20)
    private String area;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean visible;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "navigation_item_roles", joinColumns = @JoinColumn(name = "navigation_id"))
    @Column(name = "role_name", nullable = false)
    @Builder.Default
    private Set<String> allowedRoles = new HashSet<>();
}
