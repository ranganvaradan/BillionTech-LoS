package com.los.core.creditintelligence.policystudio.repository;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyVocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CiPolicyVocabularyRepository extends JpaRepository<CiPolicyVocabulary, UUID> {
    List<CiPolicyVocabulary> findByScopeLevelAndStatus(String scopeLevel, String status);
}