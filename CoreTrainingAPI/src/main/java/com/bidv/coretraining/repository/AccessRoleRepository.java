package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.AccessRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRoleRepository extends JpaRepository<AccessRole, String> {
    java.util.Optional<AccessRole> findByCode(String code);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AccessRole r where r.id=:id")
    java.util.Optional<AccessRole> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
