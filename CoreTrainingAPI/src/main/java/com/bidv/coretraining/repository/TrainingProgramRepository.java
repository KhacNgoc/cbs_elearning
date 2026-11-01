package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.TrainingProgram;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    long countByDomainIdAndStatusNot(String domainId, String status);

    @Query("SELECT p FROM TrainingProgram p WHERE (:domainId IS NULL OR :domainId = '' OR p.domain.id = :domainId) " +
           "AND p.status <> 'DELETED' " +
           "AND (:search IS NULL OR :search = '' " +
           "     OR p.name ILIKE CONCAT('%', :search, '%') " +
           "     OR p.code ILIKE CONCAT('%', :search, '%') " +
           "     OR EXISTS (SELECT 1 FROM Course c WHERE c.program.id = p.id AND c.status <> 'DELETED' " +
           "                AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))) " +
           "ORDER BY p.code ASC, p.name ASC")
    Page<TrainingProgram> findByDomainIdWithFilter(
            @Param("domainId") String domainId,
            @Param("search") String search,
            Pageable pageable);
}
