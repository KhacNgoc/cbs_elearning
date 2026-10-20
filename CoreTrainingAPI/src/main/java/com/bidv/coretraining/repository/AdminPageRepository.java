package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.AdminPage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminPageRepository extends JpaRepository<AdminPage, String> {
    java.util.List<AdminPage> findAllByOrderBySortOrderAsc();
}
