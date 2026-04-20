package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowConfigRequest {

    @NotBlank(message = "Workflow name is required")
    private String name;

    @NotNull(message = "Borrower type is required")
    private BorrowerType borrowerType;

    @NotBlank(message = "Loan product is required")
    private String loanProduct;

    private List<Map<String, Object>> steps;
}
