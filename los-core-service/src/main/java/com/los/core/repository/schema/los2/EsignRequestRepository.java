package com.los.core.repository.schema.los2;

import com.los.core.model.entity.schema.los2.EsignRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EsignRequestRepository extends JpaRepository<EsignRequest, UUID> {

    List<EsignRequest> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<EsignRequest> findTopByApplicationIdAndProviderRequestIdOrderByCreatedAtDesc(
            UUID applicationId, String providerRequestId);

    Optional<EsignRequest> findTopByProviderRequestIdOrderByCreatedAtDesc(String providerRequestId);

    /**
     * Idempotent reuse: latest active row for same app, document type, provider, signer email, and esign step (stored in raw_response JSON).
     */
    @Query(value = """
            SELECT e.*
            FROM esign_requests e
            WHERE e.application_id = :applicationId
              AND e.document_type = :documentType
              AND e.provider = :provider
              AND e.status IN ('INITIATED', 'PENDING')
              AND e.signing_url IS NOT NULL
              AND trim(e.signing_url) <> ''
              AND e.provider_request_id IS NOT NULL
              AND trim(e.provider_request_id) <> ''
              AND e.provider_request_id NOT LIKE 'FAILED-%'
              AND (e.raw_response IS NOT NULL)
              AND (e.raw_response ->> 'losSignerEmail') = :signerEmail
              AND (e.raw_response ->> 'losEsignStep') = :esignStepType
            ORDER BY e.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<EsignRequest> findLatestReusableSigningRow(
            @Param("applicationId") UUID applicationId,
            @Param("documentType") String documentType,
            @Param("provider") String provider,
            @Param("signerEmail") String signerEmail,
            @Param("esignStepType") String esignStepType);

    @Modifying
    @Query("delete from EsignRequest e where e.applicationId in :ids")
    int deleteByApplicationIdIn(@Param("ids") Collection<UUID> ids);
}
