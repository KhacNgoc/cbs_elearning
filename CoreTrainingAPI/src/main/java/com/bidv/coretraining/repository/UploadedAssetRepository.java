package com.bidv.coretraining.repository;
import com.bidv.coretraining.entity.UploadedAsset;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UploadedAssetRepository extends JpaRepository<UploadedAsset,String> {}
