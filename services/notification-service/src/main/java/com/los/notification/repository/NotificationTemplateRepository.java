package com.los.notification.repository;

import com.los.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTemplateCodeAndChannelAndActiveTrue(String templateCode, String channel);

    List<NotificationTemplate> findByTemplateCodeAndActiveTrue(String templateCode);

    List<NotificationTemplate> findByChannelAndActiveTrue(String channel);

    List<NotificationTemplate> findByActiveTrue();
}
