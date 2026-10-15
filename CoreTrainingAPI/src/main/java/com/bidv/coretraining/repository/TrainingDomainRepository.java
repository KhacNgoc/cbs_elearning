package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingDomainRepository extends JpaRepository<TrainingDomain, String> {
}
