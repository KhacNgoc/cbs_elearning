package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @org.springframework.data.jpa.repository.Query("""
        select n from Notification n where n.recipient.username = :username
          and (n.createdAt < :beforeTime or (n.createdAt = :beforeTime and n.id < :beforeId))
        order by n.createdAt desc, n.id desc
        """)
    List<Notification> findOlder(@org.springframework.data.repository.query.Param("username") String username,
        @org.springframework.data.repository.query.Param("beforeTime") java.time.LocalDateTime beforeTime,
        @org.springframework.data.repository.query.Param("beforeId") Long beforeId,
        org.springframework.data.domain.Pageable pageable);
    long countByRecipientUsernameAndReadFalse(String username);
    List<Notification> findByRecipientUsernameOrderByCreatedAtDescIdDesc(String username, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Notification n set n.read = true where n.recipient.username = :username and n.read = false")
    int markAllRead(@org.springframework.data.repository.query.Param("username") String username);
    List<Notification> findTop50ByRecipientUsernameOrderByCreatedAtDesc(String username);

    Optional<Notification> findByIdAndRecipientUsername(Long id, String username);
}
