package com.los.notification.repository;

import com.los.notification.entity.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);

    Page<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    List<NotificationLog> findByStatusAndRetryCountLessThan(String status, int maxRetry);

    long countByStatus(String status);
}
