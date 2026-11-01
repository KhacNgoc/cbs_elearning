package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TrainingDomainRepository extends JpaRepository<TrainingDomain, String> {
    boolean existsByCode(String code);
    Optional<TrainingDomain> findByCode(String code);

    @Query("SELECT COALESCE(MAX(d.seqNumber), 0) FROM TrainingDomain d WHERE d.businessModule = :module")
    Integer findMaxSeqNumberByBusinessModule(@Param("module") String module);

    @Query("SELECT d FROM TrainingDomain d WHERE d.status <> 'DELETED' " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR d.name ILIKE CONCAT('%', :search, '%') " +
           "     OR d.code ILIKE CONCAT('%', :search, '%') " +
           "     OR EXISTS (SELECT 1 FROM TrainingProgram p WHERE p.domain.id = d.id AND p.status <> 'DELETED' " +
           "                AND (p.name ILIKE CONCAT('%', :search, '%') " +
           "                     OR p.code ILIKE CONCAT('%', :search, '%') " +
           "                     OR EXISTS (SELECT 1 FROM Course c WHERE c.program.id = p.id AND c.status <> 'DELETED' " +
           "                                AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))))) " +
           "ORDER BY d.code ASC, d.name ASC")
    Page<TrainingDomain> findByFilter(
            @Param("search") String search,
            Pageable pageable);
}

