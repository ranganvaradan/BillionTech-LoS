# Collateral & AA — Phase 2 Integration Spec
## Branch: `feature/collateral-aa`
## Repo: `ranganvaradan/BillionTech-LoS`

---

## CONTEXT

Phase 1 built the UI and backend skeleton for Collateral and AA modules.
Phase 2 wires 6 real integrations. All follow the existing provider pattern
established by `AuthbridgeKycProvider`, `KarzaKycProvider`, etc.

**Key pattern to follow:**
- Interface: `IKycProvider` (for KYC-style checks) or new `ICollateralProvider`
- Registration: `@Component("providerNameHere")`
- Routing: `IntegrationProviderMatrixService` maps step types to providers
- Config: `application.yml` / environment variables for API keys/URLs
- Simulation mode: every provider must have a `simulation: true` fallback

---

## INTEGRATION 1: CKYC Upload (Complete the Stub)

### What exists
`CkycRegistryKycProvider.java` — upload returns hardcoded ACCEPTED.

### What to build

**File: `service/integration/providers/impl/CkycRegistryKycProvider.java`** (MODIFY)

Replace the stub upload block with a real HTTP call to the CKYC Central
Registry API (via Karza or Authbridge as intermediary):

```java
// Upload flow:
// 1. Build CKYC upload payload from KYC step result data
// 2. POST to configured CKYC upload endpoint
// 3. Parse KIN (CKYC Identification Number) from response
// 4. Return KycVerificationResult with KIN in referenceId

// Config properties needed:
// integration.ckyc.upload-url
// integration.ckyc.api-key
// integration.ckyc.simulation=true
```

**Response shape to handle:**
```json
{
  "status": "SUCCESS",
  "kin": "12345678901234",
  "message": "CKYC record uploaded successfully"
}
```

**KycStepType:** Already has `CKYC_UPLOAD` — no enum change needed.

---

## INTEGRATION 2: Vahan Vehicle Registration Check

### What to build

**Step 1 — Add enum value**
**File: `model/enums/KycStepType.java`** (MODIFY)
```java
VEHICLE_RC_VERIFY,   // Vahan RC verification
```

**Step 2 — Add provider type**
**File: `model/enums/ProviderType.java`** (MODIFY)
```java
VAHAN,
```

**Step 3 — New provider**
**File: `service/integration/providers/impl/VahanRcProvider.java`** (NEW)

```java
/**
 * Vahan.gov.in vehicle registration certificate verification.
 * Used for VEHICLE collateral type — verifies RC number, owner name,
 * vehicle class, fuel type, insurance validity, hypothecation status.
 *
 * Simulation mode returns realistic dummy data when
 * integration.vahan.simulation=true (default in dev).
 *
 * Real API: POST https://api.karza.in/v2/rc-advance
 * (Karza acts as Vahan aggregator — same API key as KYC)
 */
@Component("vahanRcProvider")
@RequiredArgsConstructor
public class VahanRcProvider implements IKycProvider {

    // Config:
    // integration.vahan.base-url (default: Karza RC endpoint)
    // integration.vahan.api-key
    // integration.vahan.simulation=true

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        // payload keys: rcNumber, registrationDate (optional)
        // Call Karza /v2/rc-advance with rcNumber
        // Parse: ownerName, vehicleClass, fuelType, insuranceUpto,
        //        hypothecationBank, fitnessUpto, registrationDate
        // Return result with all parsed fields in resultData map
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return stepType == KycStepType.VEHICLE_RC_VERIFY;
    }
}
```

**Step 4 — Register in routing matrix**
**File: `service/integration/IntegrationProviderMatrixService.java`** (MODIFY)
Add `VEHICLE_RC_VERIFY` → `VAHAN` mapping.

**Step 5 — Controller endpoint**
**File: `controller/CollateralController.java`** (MODIFY)
```java
@PostMapping("/vehicle/rc-verify")
public ResponseEntity<?> verifyVehicleRc(
    @RequestParam String rcNumber,
    @RequestParam UUID applicationId) {
    // Call VahanRcProvider via IntegrationRouterService
    // Save result to collateral valuation details
}
```

**Step 6 — UI button**
**File: `ui-service/src/components/CollateralPanel.tsx`** (MODIFY)
For VEHICLE type collateral rows with status PENDING, show
"Verify RC" button that calls the new endpoint and refreshes the panel.

---

## INTEGRATION 3: Technical Valuation — Property (Geo-tag + EC)

This covers two sub-integrations for PROPERTY collateral:

### 3A: Encumbrance Certificate (EC) Verification

**File: `model/enums/KycStepType.java`** (MODIFY)
```java
PROPERTY_EC_VERIFY,   // Encumbrance certificate check
```

**File: `service/integration/providers/impl/PropertyEcProvider.java`** (NEW)

```java
/**
 * Encumbrance Certificate verification via NeSL or state registration APIs.
 * Checks property for existing charges, mortgages, liens.
 *
 * Providers (configurable): KARZA or AUTHBRIDGE both offer EC search.
 * Payload: propertyRegNumber, district, state, surveyNumber
 * Returns: encumbranceStatus, existingCharges[], ownerHistory[]
 *
 * integration.property-ec.provider=KARZA
 * integration.property-ec.simulation=true
 */
```

### 3B: Geo-tagging / Location Verification

**File: `service/collateral/PropertyGeoTagService.java`** (NEW)

```java
/**
 * Property geo-location verification.
 * Uses Google Maps Geocoding API to validate address,
 * get lat/long coordinates, and verify pin code.
 *
 * Stores geo data in collateral_valuations.details JSONB:
 * { "lat": 12.9716, "lng": 77.5946, "formattedAddress": "...",
 *   "pinCode": "560001", "geoVerified": true }
 *
 * Config:
 * integration.google-maps.api-key
 * integration.google-maps.simulation=true
 */
@Service
public class PropertyGeoTagService {
    public Map<String, Object> geoTagProperty(String address, UUID valuationId) {
        // Call Google Maps Geocoding API
        // Parse lat, lng, formattedAddress, pinCode
        // Update collateral_valuations.details with geo data
        // Return geo result
    }
}
```

**File: `controller/CollateralController.java`** (MODIFY)
```java
@PostMapping("/{valuationId}/geo-tag")
public ResponseEntity<?> geoTagProperty(
    @PathVariable UUID valuationId,
    @RequestParam String address) {
    // Calls PropertyGeoTagService
}

@PostMapping("/{valuationId}/ec-verify")
public ResponseEntity<?> verifyEncumbrance(
    @PathVariable UUID valuationId,
    @RequestBody Map<String, String> propertyDetails) {
    // Calls PropertyEcProvider
}
```

**UI additions in CollateralPanel.tsx:**
For PROPERTY rows — show two buttons: "Verify EC" and "Geo-tag Property"
Show geo coordinates and EC status in the valuation details card.

---

## INTEGRATION 4: Gold Standard Valuation

### What to build

**File: `service/collateral/GoldValuationService.java`** (NEW)

```java
/**
 * Gold standard valuation using live MCX/IBJA gold rates.
 *
 * Calculation:
 *   Pure Gold Value = Weight(grams) × Purity(%) × Live Rate(per gram)
 *   Accepted Value  = Pure Gold Value × (1 - haircut)
 *   Default haircut = 25% (configurable)
 *   Max LTV for gold = 75% (RBI guideline)
 *
 * Rate source: MCX API or IBJA daily rate
 * Config:
 *   integration.gold-rate.provider=MCX  (or IBJA or MANUAL)
 *   integration.gold-rate.api-key
 *   integration.gold-rate.simulation=true
 *   collateral.gold.haircut=0.25
 *   collateral.gold.max-ltv=75
 */
@Service
public class GoldValuationService {

    public GoldValuationResult calculateGoldValue(
            BigDecimal weightGrams,
            BigDecimal purityPercent,   // e.g. 91.6 for 22K
            String articleDescription) {
        // 1. Fetch live gold rate from MCX API (or use last cached rate)
        // 2. Calculate pure gold weight: weight × (purity/100)
        // 3. Calculate market value: pureWeight × liveRate
        // 4. Apply haircut for accepted value
        // 5. Return GoldValuationResult with all fields
    }

    public BigDecimal fetchLiveGoldRate() {
        // GET https://api.mcxindia.com/gold-rate (or similar)
        // Cache rate for 1 hour (use Spring @Cacheable)
        // Fallback to simulation rate in dev
    }
}
```

**New DTO:**
```java
public record GoldValuationResult(
    BigDecimal weightGrams,
    BigDecimal purityPercent,
    BigDecimal liveRatePerGram,
    BigDecimal pureGoldWeight,
    BigDecimal marketValue,
    BigDecimal haircut,
    BigDecimal acceptedValue,
    BigDecimal maxLtvPercent,
    Instant rateTimestamp
) {}
```

**File: `controller/CollateralController.java`** (MODIFY)
```java
@PostMapping("/{valuationId}/gold-value")
public ResponseEntity<GoldValuationResult> calculateGoldValue(
    @PathVariable UUID valuationId,
    @RequestParam BigDecimal weightGrams,
    @RequestParam BigDecimal purityPercent) {
    // Calls GoldValuationService
    // Auto-completes the valuation with calculated values
}
```

**UI in CollateralPanel.tsx:**
For GOLD rows — show "Calculate Value" button that opens a mini-form:
- Weight (grams)
- Purity (dropdown: 24K=99.9%, 22K=91.6%, 18K=75%, Other)
- Clicking "Calculate" calls the endpoint and auto-fills market value

---

## INTEGRATION 5: Account Aggregator — Real Provider

### What to build

**File: `model/enums/ProviderType.java`** (MODIFY)
```java
SETU_AA,     // Setu Account Aggregator
FINVU_AA,    // Finvu AA (alternative)
PERFIOS_AA,  // Perfios AA
```

**File: `service/aa/providers/IAccountAggregatorProvider.java`** (NEW)
```java
public interface IAccountAggregatorProvider {
    AaConsentResponse createConsent(AaConsentRequest request);
    AaConsentStatus checkConsentStatus(String consentHandle);
    AaFetchResponse fetchFinancialData(String consentHandle);
    void revokeConsent(String consentId, String reason);
    String getProviderName();
}
```

**File: `service/aa/providers/impl/SetuAaProvider.java`** (NEW)
```java
/**
 * Setu Account Aggregator integration.
 * Docs: https://docs.setu.co/data/account-aggregator
 *
 * Flow:
 * 1. POST /consent — create consent request, get consentHandle + redirect URL
 * 2. Borrower approves on AA app (redirect flow)
 * 3. Webhook callback → POST /aa/consent/callback
 * 4. POST /sessions — create FI data session
 * 5. GET /sessions/{sessionId} — poll for data
 * 6. Parse FI data (DEPOSIT, TD, etc.) into AaFetchedData
 *
 * Config:
 * integration.setu-aa.base-url=https://fiu-uat.setu.co
 * integration.setu-aa.client-id=
 * integration.setu-aa.client-secret=
 * integration.setu-aa.redirect-url=https://los.billiontech.ai/aa/callback
 * integration.setu-aa.simulation=true
 */
```

**File: `controller/AccountAggregatorController.java`** (MODIFY)
Add webhook callback endpoint:
```java
@PostMapping("/consent/callback")
public ResponseEntity<Void> handleAaCallback(
    @RequestBody Map<String, Object> callbackPayload) {
    // Parse consentHandle and status from Setu webhook
    // Update aa_consents record
    // If APPROVED, trigger data fetch automatically
}
```

**File: `service/aa/AccountAggregatorService.java`** (MODIFY)
- Route `createConsentRequest` through `SetuAaProvider` (or simulation)
- Route `fetchData` through real provider fetch session
- Parse real FI XML/JSON into `AaFetchedData` structure
- Store parsed summary in `fetchedDataSummary` JSONB column

**UI in AaConsentPanel.tsx:**
- When PENDING, show "Open AA App" button with redirect URL from consent response
- Add webhook status polling (poll every 10s when PENDING)

---

## INTEGRATION 6: CERSAI Registration & Verification

### What to build

**Step 1 — New entity**
**File: `model/entity/CersaiRegistration.java`** (NEW)
```java
@Entity
@Table(name = "cersai_registrations")
public class CersaiRegistration {
    UUID id;
    UUID applicationId;
    UUID collateralValuationId;
    String cersaiId;           // Assigned by CERSAI
    String assetType;          // IMMOVABLE / MOVABLE / INTANGIBLE
    String assetDescription;
    String registrationStatus; // PENDING / REGISTERED / FAILED / SEARCHED
    String securityInterestType; // MORTGAGE / HYPOTHECATION / PLEDGE / LIEN
    BigDecimal securedAmount;
    String borrowerName;
    String borrowerPan;
    String lenderName;
    String lenderCin;
    Instant registrationDate;
    Instant expiryDate;
    Map<String, Object> responseData; // JSONB
    Instant createdAt;
    Instant updatedAt;
}
```

**Step 2 — Migration**
**File: `db/migration/V65__cersai_registrations.sql`** (NEW)
```sql
CREATE TABLE IF NOT EXISTS cersai_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    collateral_valuation_id UUID REFERENCES collateral_valuations(id),
    cersai_id VARCHAR(50),
    asset_type VARCHAR(30) NOT NULL,
    asset_description TEXT,
    registration_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    security_interest_type VARCHAR(30) NOT NULL,
    secured_amount DECIMAL(15,2),
    borrower_name VARCHAR(200),
    borrower_pan VARCHAR(10),
    lender_name VARCHAR(200),
    lender_cin VARCHAR(21),
    registration_date TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    response_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_cersai_application ON cersai_registrations(application_id);
CREATE INDEX idx_cersai_id ON cersai_registrations(cersai_id);
```

**Step 3 — Service**
**File: `service/collateral/CersaiService.java`** (NEW)
```java
/**
 * CERSAI (Central Registry of Securitisation Asset Reconstruction
 * and Security Interest) integration.
 *
 * Two operations:
 * 1. REGISTER — file security interest after sanction/disbursement
 * 2. SEARCH   — search existing charges on collateral before sanction
 *
 * API: CERSAI SFMS (Secure Financial Messaging System)
 * Provider: Direct CERSAI API or via Karza/Authbridge intermediary
 *
 * Config:
 * integration.cersai.base-url
 * integration.cersai.institution-id
 * integration.cersai.api-key
 * integration.cersai.simulation=true
 */
@Service
public class CersaiService {

    public CersaiRegistration searchCharges(
            String assetType,
            String assetIdentifier,  // Survey no / RC no / etc.
            UUID applicationId) {
        // Search CERSAI for existing security interests on the asset
        // Returns list of existing charges if any
    }

    public CersaiRegistration registerSecurityInterest(
            UUID applicationId,
            UUID collateralValuationId,
            CersaiRegistrationRequest request) {
        // POST to CERSAI to register security interest
        // Store CERSAI ID returned
        // Update collateral_valuations with CERSAI registration status
    }

    public CersaiRegistration getRegistration(UUID applicationId) {
        // Fetch latest registration for application
    }
}
```

**Step 4 — Controller**
**File: `controller/CersaiController.java`** (NEW)
```java
@RestController
@RequestMapping("/api/v1/cersai")
public class CersaiController {

    @PostMapping("/search")
    public ResponseEntity<?> searchCharges(...)

    @PostMapping("/register")
    public ResponseEntity<CersaiRegistration> register(...)

    @GetMapping("/application/{applicationId}")
    public ResponseEntity<CersaiRegistration> getByApplication(...)
}
```

**Step 5 — Frontend API**
**File: `ui-service/src/api/cersai.ts`** (NEW)
```typescript
export const cersaiApi = {
  search: (assetType: string, assetIdentifier: string, applicationId: string) =>
    http.post('/cersai/search', null, { params: { assetType, assetIdentifier, applicationId } }),

  register: (data: CersaiRegistrationRequest) =>
    http.post<CersaiRegistration>('/cersai/register', data),

  getByApplication: (applicationId: string) =>
    http.get<CersaiRegistration>(`/cersai/application/${applicationId}`),
}
```

**Step 6 — UI in CollateralPanel.tsx**
Add a "CERSAI" section below the valuations table:
- "Search Charges" button — searches before sanction
- "Register Security Interest" button (shown after sanction) — files with CERSAI
- Shows CERSAI ID and registration status badge

---

## DATABASE MIGRATIONS SUMMARY

| File | Content |
|------|---------|
| V64 (done) | collateral_ltv_ratio, aa_consent_status, foir_from_aa |
| V65 (new) | cersai_registrations table |

---

## CONFIG PROPERTIES TO ADD

Add to `application.yml` (with simulation defaults):

```yaml
integration:
  ckyc:
    upload-url: ${CKYC_UPLOAD_URL:}
    api-key: ${CKYC_API_KEY:}
    simulation: ${CKYC_SIMULATION:true}
  vahan:
    base-url: ${VAHAN_BASE_URL:https://api.karza.in/v2}
    api-key: ${KARZA_API_KEY:}
    simulation: ${VAHAN_SIMULATION:true}
  property-ec:
    provider: ${PROPERTY_EC_PROVIDER:KARZA}
    simulation: ${PROPERTY_EC_SIMULATION:true}
  google-maps:
    api-key: ${GOOGLE_MAPS_API_KEY:}
    simulation: ${GEO_SIMULATION:true}
  gold-rate:
    provider: ${GOLD_RATE_PROVIDER:SIMULATION}
    api-key: ${GOLD_RATE_API_KEY:}
    simulation: ${GOLD_SIMULATION:true}
  setu-aa:
    base-url: ${SETU_AA_URL:https://fiu-uat.setu.co}
    client-id: ${SETU_CLIENT_ID:}
    client-secret: ${SETU_CLIENT_SECRET:}
    redirect-url: ${SETU_REDIRECT_URL:https://los.billiontech.ai/aa/callback}
    simulation: ${SETU_SIMULATION:true}
  cersai:
    base-url: ${CERSAI_URL:}
    institution-id: ${CERSAI_INSTITUTION_ID:}
    api-key: ${CERSAI_API_KEY:}
    simulation: ${CERSAI_SIMULATION:true}

collateral:
  gold:
    haircut: 0.25
    max-ltv: 75
  property:
    max-ltv: 80
  vehicle:
    max-ltv: 70
  fixed-deposit:
    max-ltv: 90
```

---

## FILES TO CREATE (Phase 2)

```
# Backend
service/integration/providers/impl/VahanRcProvider.java
service/integration/providers/impl/PropertyEcProvider.java
service/collateral/GoldValuationService.java
service/collateral/PropertyGeoTagService.java
service/collateral/CersaiService.java
service/aa/providers/IAccountAggregatorProvider.java
service/aa/providers/impl/SetuAaProvider.java
service/aa/providers/impl/SimulatedAaProvider.java
controller/CersaiController.java
model/entity/CersaiRegistration.java
model/dto/GoldValuationResult.java
model/dto/CersaiRegistrationRequest.java
db/migration/V65__cersai_registrations.sql

# Frontend
ui-service/src/api/cersai.ts
```

## FILES TO MODIFY (Phase 2)

```
# Backend
model/enums/KycStepType.java          — add VEHICLE_RC_VERIFY, PROPERTY_EC_VERIFY
model/enums/ProviderType.java         — add VAHAN, SETU_AA, FINVU_AA, CERSAI
service/integration/providers/impl/CkycRegistryKycProvider.java  — wire real upload
service/integration/IntegrationProviderMatrixService.java         — add new step types
service/aa/AccountAggregatorService.java  — route through real provider
controller/CollateralController.java   — add RC verify, EC verify, geo-tag, gold-value endpoints
controller/AccountAggregatorController.java  — add callback webhook

# Frontend
ui-service/src/components/CollateralPanel.tsx  — add RC verify, EC, geo-tag, gold calc, CERSAI buttons
ui-service/src/components/AaConsentPanel.tsx   — add redirect URL, polling
```

---

## TESTING CHECKLIST (Phase 2)

### CKYC
- [ ] CKYC download works (already passing via Authbridge)
- [ ] CKYC upload sends real payload and returns KIN

### Vahan
- [ ] Vehicle RC verify button appears for VEHICLE collateral
- [ ] RC verification returns owner name, vehicle class, hypothecation status
- [ ] Simulation mode returns realistic dummy data

### Property
- [ ] EC verify returns encumbrance status and existing charges
- [ ] Geo-tag stores lat/lng in collateral details
- [ ] Coordinates visible in CollateralPanel

### Gold
- [ ] Gold value calculator appears for GOLD collateral
- [ ] Calculates correctly: weight × purity × live rate
- [ ] Haircut applied (25% default)
- [ ] Max LTV shows as 75%

### Account Aggregator
- [ ] Setu AA consent URL generated
- [ ] Callback webhook updates consent status
- [ ] Real FI data parsed into summary display
- [ ] FOIR calculated from real bank data

### CERSAI
- [ ] Search charges returns existing liens on asset
- [ ] Register security interest returns CERSAI ID
- [ ] CERSAI status badge visible in CollateralPanel
