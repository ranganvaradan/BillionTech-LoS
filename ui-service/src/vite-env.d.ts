/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL for los-core-service APIs, e.g. `https://api.example.com/api/v1` (no trailing slash). */
  readonly VITE_API_BASE_URL?: string
  /** When `true`, show Staging badge and Demo Samples under Policies (staging UI builds). */
  readonly VITE_STAGING_DEMO?: string
  /**
   * Optional CI internal token for `/api/v1/internal/**` (X-Internal-Token).
   * Set per-environment at build time when the target core has a non-blank token.
   */
  readonly VITE_CREDIT_INTELLIGENCE_INTERNAL_TOKEN?: string
}
