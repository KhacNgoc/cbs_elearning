package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingProgram;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, String> {
    java.util.List<TrainingProgram> findByDomainId(String domainId);
}
