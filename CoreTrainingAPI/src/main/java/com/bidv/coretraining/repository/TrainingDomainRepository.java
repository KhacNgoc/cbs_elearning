package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TrainingDomainRepository extends JpaRepository<TrainingDomain, String> {
    boolean existsByCode(String code);
    Optional<TrainingDomain> findByCode(String code);

    @Query("SELECT COALESCE(MAX(d.seqNumber), 0) FROM TrainingDomain d WHERE d.businessModule = :module")
    Integer findMaxSeqNumberByBusinessModule(@Param("module") String module);
}
