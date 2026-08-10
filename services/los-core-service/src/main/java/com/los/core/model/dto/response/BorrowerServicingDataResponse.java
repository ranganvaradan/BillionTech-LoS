package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Wraps a post-disbursement servicing dataset (repayment schedule, statement, transactions) together with its
 * provenance so the borrower portal can label the data honestly instead of a blanket "demo" disclaimer.
 *
 * <p>{@code source} values:
 * <ul>
 *   <li>{@code LMS} — pulled live from the loan management system (Encore).</li>
 *   <li>{@code LOCAL} — derived from LOS records (sanction terms, recorded disbursal/repayments). Real, but the
 *       official LMS ledger had nothing to return yet (e.g. just-disbursed loan with no posted entries).</li>
 * </ul>
 */
@Value
@Builder
public class BorrowerServicingDataResponse<T> {
    /** {@code LMS} or {@code LOCAL}. */
    String source;
    List<T> rows;
}
