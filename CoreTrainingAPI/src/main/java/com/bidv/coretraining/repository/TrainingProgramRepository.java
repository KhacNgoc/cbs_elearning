package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, String> {
    List<TrainingProgram> findByDomainId(String domainId);
    boolean existsByCode(String code);
    Optional<TrainingProgram> findByCode(String code);

    @Query("SELECT COALESCE(MAX(p.seqNumber), 0) FROM TrainingProgram p WHERE p.businessModule = :module")
    Integer findMaxSeqNumberByBusinessModule(@Param("module") String module);
}
