package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.NavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NavigationItemRepository extends JpaRepository<NavigationItem, String> {
    List<NavigationItem> findAllByOrderBySortOrderAsc();
}
