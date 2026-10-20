package com.bidv.coretraining.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "admin_pages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPage {
    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String route;
    private int sortOrder;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_page_actions", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "action", nullable = false)
    @Builder.Default
    private Set<String> actions = new HashSet<>();
}
