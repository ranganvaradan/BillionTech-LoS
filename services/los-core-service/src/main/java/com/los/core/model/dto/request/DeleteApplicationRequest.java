package com.los.core.model.dto.request;

import lombok.Data;

@Data
public class DeleteApplicationRequest {

    /** Optional note stored in the deletion audit log. */
    private String reason;

    /**
     * Required when the application has an active loan lifecycle state (sanctioned / disbursed, etc.).
     */
    private boolean confirmActiveLoan;
}
