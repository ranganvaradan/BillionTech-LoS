package com.billiontech.bankstatement.repository;

import com.billiontech.bankstatement.model.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyHashAndIsActiveTrue(String keyHash);
}
