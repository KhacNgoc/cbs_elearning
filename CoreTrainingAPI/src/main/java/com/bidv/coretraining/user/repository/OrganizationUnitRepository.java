package com.bidv.coretraining.user.repository;

import com.bidv.coretraining.user.entity.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, String> {
}
