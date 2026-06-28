package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class BorrowerProgramsResponse {
    boolean linked;
    String message;
    List<BorrowerProgramEnrollmentResponse> enrollments;
}
