package com.bidv.coretraining.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavigationItemDto {
    private String id;
    private String label;
    private String route;
    private String icon;
    private String area;
    private Integer sortOrder;
    private Boolean visible;
}
