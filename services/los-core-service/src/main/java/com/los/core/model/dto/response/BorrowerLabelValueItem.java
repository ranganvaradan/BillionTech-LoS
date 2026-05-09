package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BorrowerLabelValueItem {
    String label;
    String value;
}
