package com.los.lms.job;

import com.los.encore.client.api.EncoreLmsApi;
import com.los.encore.client.logging.LmsLogEvent;
import com.los.lms.entity.LmsAccountSummary;
import com.los.lms.entity.LmsLoanHandover;
import com.los.lms.repository.LmsAccountSummaryRepository;
import com.los.lms.repository.LmsLoanHandoverRepository;
import com.los.lms.support.EncoreSummaryNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nightly (or cron-driven) sync of Encore summaries into local {@code lms_account_summary} — subset of legacy
 * {@code EncoreJobsServiceFacadeImpl} behaviour without SSO/invoice pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "los.lms.encore.sync", name = "enabled", havingValue = "true")
public class EncoreSummarySyncJob {

    private final EncoreLmsApi encoreLmsApi;
    private final LmsLoanHandoverRepository handoverRepository;
    private final LmsAccountSummaryRepository summaryRepository;

    @Scheduled(cron = "${los.lms.encore.sync.cron:0 0 2 * * *}")
    @Transactional
    public void syncSummariesFromEncore() {
        if (!encoreLmsApi.isActive()) {
            return;
        }
        List<LmsLoanHandover> handovers = handoverRepository.findAllByEncoreAccountIdIsNotNull();
        int ok = 0;
        int failed = 0;
        for (LmsLoanHandover h : handovers) {
            try {
                List<Map<String, Object>> summaries = encoreLmsApi.findSummaries(List.of(h.getEncoreAccountId()));
                if (summaries.isEmpty()) {
                    continue;
                }
                Map<String, Object> encoreData = summaries.get(0);
                Optional<LmsAccountSummary> opt = summaryRepository.findByApplicationNumber(h.getApplicationNumber());
                if (opt.isEmpty()) {
                    continue;
                }
                LmsAccountSummary entity = opt.get();
                EncoreSummaryNormalizer.applyEncoreRowToAccountSummary(entity, encoreData);
                summaryRepository.save(entity);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("event={} application={} msg={}", LmsLogEvent.LMS_SYNC_RETRY,
                        h.getApplicationNumber(), e.getMessage());
            }
        }
        log.info("event={} handovers={} updated={} failed={}", LmsLogEvent.LMS_SYNC_COMPLETED,
                handovers.size(), ok, failed);
    }
}
