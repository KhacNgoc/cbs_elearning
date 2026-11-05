package com.bidv.coretraining.document.repository;

import com.bidv.coretraining.document.entity.UploadedAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedAssetRepository extends JpaRepository<UploadedAsset, String> {
}
