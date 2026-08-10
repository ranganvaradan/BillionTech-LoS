package com.los.core.creditintelligence.bureau.repository;

import com.los.core.creditintelligence.bureau.domain.CiBureauPaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CiBureauPaymentHistoryRepository extends JpaRepository<CiBureauPaymentHistory, UUID> {

    List<CiBureauPaymentHistory> findByTradelineIdOrderByMonthDesc(UUID tradelineId);
}
