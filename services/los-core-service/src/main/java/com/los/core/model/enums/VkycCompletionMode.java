package com.los.core.model.enums;

/**
 * How the VKYC workflow checkpoint was satisfied. {@code null} on legacy rows means the
 * standard video / HyperVerge path (or manual stage progression) without PKYC override.
 */
public enum VkycCompletionMode {
    PKYC
}
