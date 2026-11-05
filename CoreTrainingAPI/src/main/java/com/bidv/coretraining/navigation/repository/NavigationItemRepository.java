package com.bidv.coretraining.navigation.repository;

import com.bidv.coretraining.navigation.entity.NavigationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NavigationItemRepository extends JpaRepository<NavigationItem, String> {
    List<NavigationItem> findAllByOrderBySortOrderAsc();
}
