package com.bidv.coretraining.access.repository;

import com.bidv.coretraining.access.entity.AdminPage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminPageRepository extends JpaRepository<AdminPage, String> {
    java.util.List<AdminPage> findAllByOrderBySortOrderAsc();
}
