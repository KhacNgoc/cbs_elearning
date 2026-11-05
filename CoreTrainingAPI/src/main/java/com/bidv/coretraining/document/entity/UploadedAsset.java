package com.bidv.coretraining.document.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedAsset {
    @Id
    private String path;
    @Column(nullable = false)
    private String uploadedBy;
}
