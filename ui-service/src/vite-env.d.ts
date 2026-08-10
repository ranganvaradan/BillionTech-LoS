/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL for los-core-service APIs, e.g. `https://api.example.com/api/v1` (no trailing slash). */
  readonly VITE_API_BASE_URL?: string
  /** When `true`, show Staging badge and Demo Samples under Policies (staging UI builds). */
  readonly VITE_STAGING_DEMO?: string
}
