package com.bidv.coretraining.repository;

import com.bidv.coretraining.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientUsernameOrderByCreatedAtDesc(String username);

    Optional<Notification> findByIdAndRecipientUsername(Long id, String username);
}
