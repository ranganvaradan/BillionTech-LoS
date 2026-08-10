package com.los.core.model.dto.request;

import lombok.Data;

@Data
public class AiLosOpenRequest {
    /**
     * Absolute LOS URL to return back to after AI LOS review.
     */
    private String returnUrl;
    /**
     * REVIEW (default) or WHAT_IF.
     */
    private String mode;
}
