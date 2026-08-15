#!/usr/bin/env bash
# NEW-CLIENT-CLEAN-ROOM-UAT-1 — Client (:8084) clean lender config + controlled E2E
# Does NOT touch Internal DB. Does NOT activate Day-1 15 Categories. No live UW cutover.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8084/api/v1}"
TOKEN="${CREDIT_INTELLIGENCE_INTERNAL_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
  TOKEN=$(docker exec billiontechlos-core-client printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN 2>/dev/null || true)
fi
HDR=(-H "Content-Type: application/json" -H "X-User-Id: 00000000-0000-0000-0000-0000000000aa" -H "X-User-Role: ADMINISTRATOR" -H "X-User-Name: clean-room-uat")
if [[ -n "$TOKEN" ]]; then
  HDR+=(-H "X-Internal-Token: $TOKEN")
fi

SD="$BASE/internal/credit-intelligence/staging-demo"
DP3="$BASE/internal/credit-intelligence/dp3"
WF_ID="b0000000-0000-0000-0000-000000000011" # Default Workflow - Individual - Business Term Loan
WF_VER=2

jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
jdump() { python3 -m json.tool 2>/dev/null || cat; }

echo "=== ACTUATOR ==="
curl -sS http://127.0.0.1:8084/actuator/info | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("git",{}).get("commit",{}).get("id",{}).get("full",""))'

echo "=== POLICY A (STARTER — financial/income oriented) ==="
PA=$(curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/create" -d '{"policyName":"CLEAN UAT Policy A — STARTER LOAN","description":"Income/financial proposition for clean-client UAT"}')
echo "$PA" | jdump | head -40
DOC_A=$(echo "$PA" | jget "d.get('policyHeader',{}).get('documentId') or d.get('documentId') or ''")
echo "DOC_A=$DOC_A"
[[ -n "$DOC_A" && "$DOC_A" != "None" ]]

for RULE in \
  '{"confirm":true,"mode":"DESCRIBE","text":"Bureau score should be >= 650","treatment":"Reject"}' \
  '{"confirm":true,"mode":"DESCRIBE","text":"FOIR should not exceed 50%","treatment":"Reject"}' \
  '{"confirm":true,"mode":"BUILD","parameterId":"application.declared_income","operator":">=","value":15000,"treatment":"Reject"}'
do
  curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/documents/$DOC_A/rules/add-plain-english" -d "$RULE" | jget "d.get('confirmed') or d.get('addedRuleCount') or d.get('message') or d"
done

echo "=== POLICY B (BANK STARTER — bank-derived) ==="
PB=$(curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/create" -d '{"policyName":"CLEAN UAT Policy B — BANK STARTER","description":"Bank-statement proposition for clean-client UAT"}')
DOC_B=$(echo "$PB" | jget "d.get('policyHeader',{}).get('documentId') or d.get('documentId') or ''")
echo "DOC_B=$DOC_B"
[[ -n "$DOC_B" && "$DOC_B" != "None" ]]

for RULE in \
  '{"confirm":true,"mode":"DESCRIBE","text":"Bureau score should be >= 650","treatment":"Reject"}' \
  '{"confirm":true,"mode":"BUILD","parameterId":"banking.avg_daily_balance_3m","operator":">=","value":5000,"treatment":"Reject"}' \
  '{"confirm":true,"mode":"BUILD","parameterId":"banking.monthly_obligation","operator":"<=","value":50000,"treatment":"Manual Review"}'
do
  curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/documents/$DOC_B/rules/add-plain-english" -d "$RULE" | jget "d.get('confirmed') or d.get('addedRuleCount') or d.get('message') or d"
done

echo "=== MATERIALIZE GRAPHS ==="
curl -sS "${HDR[@]}" -X POST "$DP3/policies/$DOC_A/materialize-graph" | jdump | head -30
curl -sS "${HDR[@]}" -X POST "$DP3/policies/$DOC_B/materialize-graph" | jdump | head -30

echo "=== SCOPE SAVE (lifecycle — authoritative Scope tab source) ==="
for DOC_SCOPE in "$DOC_A" "$DOC_B"; do
  curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/documents/$DOC_SCOPE/lifecycle/save-draft" -d "{
    \"products\": [\"BUSINESS_TERM_LOAN\"],
    \"borrowerTypes\": [\"INDIVIDUAL\"],
    \"customerSegment\": \"BORROWER\",
    \"minLoanAmount\": 20000,
    \"maxLoanAmount\": 500000,
    \"effectiveFrom\": \"2026-01-01\",
    \"reasonForChange\": \"Clean-client UAT scope aligned to STARTER eligibility\"
  }" | jdump | head -25 || true
done

echo "=== CATALOGUE UPSERT + SCHEDULE A ==="
CAT_A=$(curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue" -d "{
  \"policyDocumentId\": \"$DOC_A\",
  \"policyName\": \"CLEAN UAT Policy A — STARTER LOAN\",
  \"policyVersion\": \"v1\",
  \"policyType\": \"CREDIT_POLICY\",
  \"products\": [\"BUSINESS_TERM_LOAN\"],
  \"borrowerTypes\": [\"INDIVIDUAL\"],
  \"minLoanAmount\": 20000,
  \"maxLoanAmount\": 500000,
  \"effectiveFrom\": \"2026-01-01\",
  \"dataReadinessStatus\": \"PASSED\",
  \"testsStatus\": \"PASSED\",
  \"simulationReviewStatus\": \"PASSED\",
  \"businessStatus\": \"APPROVED\",
  \"actor\": \"clean-room-uat\"
}")
APP_A=$(echo "$CAT_A" | jget "d.get('applicabilityId') or d.get('id') or ''")
echo "APP_A=$APP_A"
curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue/$APP_A/schedule" -d '{"effectiveFrom":"2026-01-01","products":["BUSINESS_TERM_LOAN"],"businessDate":"2026-01-01","actor":"clean-room-uat"}' | jdump | head -20
curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue/$APP_A/link-immutable" -d '{"publishDemoPackage":true,"actor":"clean-room-uat"}' | jdump | head -20 || true

echo "=== CATALOGUE UPSERT + SCHEDULE B ==="
CAT_B=$(curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue" -d "{
  \"policyDocumentId\": \"$DOC_B\",
  \"policyName\": \"CLEAN UAT Policy B — BANK STARTER\",
  \"policyVersion\": \"v1\",
  \"policyType\": \"CREDIT_POLICY\",
  \"products\": [\"BUSINESS_TERM_LOAN\"],
  \"borrowerTypes\": [\"INDIVIDUAL\"],
  \"minLoanAmount\": 20000,
  \"maxLoanAmount\": 500000,
  \"effectiveFrom\": \"2026-01-01\",
  \"dataReadinessStatus\": \"PASSED\",
  \"testsStatus\": \"PASSED\",
  \"simulationReviewStatus\": \"PASSED\",
  \"businessStatus\": \"APPROVED\",
  \"actor\": \"clean-room-uat\"
}")
APP_B=$(echo "$CAT_B" | jget "d.get('applicabilityId') or d.get('id') or ''")
echo "APP_B=$APP_B"
curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue/$APP_B/schedule" -d '{"effectiveFrom":"2026-01-01","products":["BUSINESS_TERM_LOAN"],"businessDate":"2026-01-01","actor":"clean-room-uat"}' | jdump | head -20
curl -sS "${HDR[@]}" -X POST "$SD/policy-catalogue/$APP_B/link-immutable" -d '{"publishDemoPackage":true,"actor":"clean-room-uat"}' | jdump | head -20 || true

echo "=== SCORECARD POLICY_WEIGHTED_V2 for Policy A (optional link) ==="
# Create via underwriting scorecards if possible; link upgrades mode
SC_BODY='{
  "name": "CLEAN UAT Starter Weighted Scorecard",
  "borrowerType": "INDIVIDUAL",
  "loanProduct": "BUSINESS_TERM_LOAN",
  "version": 1,
  "priority": 10,
  "minAmount": 20000,
  "maxAmount": 500000,
  "scorecardJson": {
    "rows": [
      {"id":"f1","parameter":"bureau.score","source":"BUREAU","condition":">=650","weight":5,"score":100},
      {"id":"f2","parameter":"application.declared_income","source":"CUSTOMER","condition":">=15000","weight":3,"score":100},
      {"id":"f3","parameter":"obligation.ratio","source":"DERIVED","condition":"<=50","weight":2,"score":100}
    ]
  },
  "thresholdsJson": {"approveMinPercent": 60, "manualMinPercent": 40},
  "hardRulesJson": {"rules": []},
  "safetyJson": {"missingDataPoliciesConfirmed": true, "factorPolicies": {}}
}'
SC=$(curl -sS "${HDR[@]}" -X POST "$BASE/underwriting/scorecards" -d "$SC_BODY" || echo '{}')
SC_ID=$(echo "$SC" | jget "d.get('id') or ''" 2>/dev/null || true)
echo "SC_ID=$SC_ID"
if [[ -n "$SC_ID" && "$SC_ID" != "None" ]]; then
  curl -sS "${HDR[@]}" -X POST "$DP3/policies/$DOC_A/link-scorecard/$SC_ID" | jdump | head -30 || true
  curl -sS "${HDR[@]}" -X POST "$DP3/scorecards/weight-preview" -d '{"weights":{"bureau.score":5,"application.declared_income":3,"obligation.ratio":2}}' | jdump | head -20 || true
fi

echo "=== CREATE CATEGORIES ==="
ACTOR='{"userId":"00000000-0000-0000-0000-0000000000aa","userName":"clean-room-uat","role":"CREDIT_MANAGER"}'

create_cat() {
  local CODE="$1" NAME="$2" APPID="$3" DOCID="$4"
  curl -sS "${HDR[@]}" -X POST "$BASE/customer-categories" -d "{
    \"code\": \"$CODE\",
    \"name\": \"$NAME\",
    \"description\": \"Clean-client UAT lending proposition\",
    \"borrowerType\": \"INDIVIDUAL\",
    \"loanProduct\": \"BUSINESS_TERM_LOAN\",
    \"intakeSegment\": \"BORROWER\",
    \"minAmount\": 20000,
    \"maxAmount\": 500000,
    \"policyApplicabilityId\": \"$APPID\",
    \"policyDocumentId\": \"$DOCID\",
    \"policyVersionLabel\": \"v1\",
    \"workflowId\": \"$WF_ID\",
    \"workflowVersion\": $WF_VER
  }"
}

# Idempotent: skip if code exists
EXIST_STARTER=$(curl -sS "${HDR[@]}" "$BASE/customer-categories" | jget "[c.get('code') for c in (d if isinstance(d,list) else d.get('items',d.get('content',[])))] if isinstance(d,(list,dict)) else []" 2>/dev/null || echo "")

CA=$(create_cat "CC_CLEAN_STARTER_LOAN" "STARTER LOAN" "$APP_A" "$DOC_A")
echo "$CA" | jdump | head -40
CAT_A_ID=$(echo "$CA" | jget "d.get('id') or ''")
CB=$(create_cat "CC_CLEAN_BANK_STARTER" "BANK STARTER" "$APP_B" "$DOC_B")
echo "$CB" | jdump | head -40
CAT_B_ID=$(echo "$CB" | jget "d.get('id') or ''")

echo "=== PROPOSITION + DISAMBIGUATION CONFIG ==="
curl -sS "${HDR[@]}" -X PUT "$BASE/customer-categories/$CAT_A_ID/proposition-config" -d '{
  "proposition": {
    "customerFacingName": "STARTER LOAN",
    "shortDescription": "Assessed primarily using income and financial information",
    "allowAutoSingleMatch": true,
    "displayOrder": 10
  },
  "disambiguation": {
    "attributes": {
      "financial_data_route": ["FINANCIAL_STATEMENTS"]
    }
  }
}' | jdump | head -20

curl -sS "${HDR[@]}" -X PUT "$BASE/customer-categories/$CAT_B_ID/proposition-config" -d '{
  "proposition": {
    "customerFacingName": "BANK STARTER",
    "shortDescription": "Assessed using bank-account information",
    "allowAutoSingleMatch": true,
    "displayOrder": 20
  },
  "disambiguation": {
    "attributes": {
      "financial_data_route": ["BANK_AA"]
    }
  }
}' | jdump | head -20

echo "=== READINESS / GOVERNANCE ==="
for ID in "$CAT_A_ID" "$CAT_B_ID"; do
  echo "--- readiness $ID ---"
  curl -sS "${HDR[@]}" "$BASE/customer-categories/$ID/activation-readiness" | jdump | head -60
  curl -sS "${HDR[@]}" -X POST "$BASE/customer-categories/$ID/submit" -d '{"remarks":"clean-room-uat"}' | jget "d.get('status') or d" || true
  curl -sS "${HDR[@]}" -X POST "$BASE/customer-categories/$ID/approve" -d '{"remarks":"clean-room-uat"}' | jget "d.get('status') or d" || true
  curl -sS "${HDR[@]}" -X POST "$BASE/customer-categories/$ID/activate" -d '{}' | jget "d.get('status') or d" || true
done

echo "=== CREATE TEST APPLICATION ==="
APP=$(curl -sS "${HDR[@]}" -X POST "$BASE/applications" -d '{
  "borrowerType": "INDIVIDUAL",
  "loanProduct": "BUSINESS_TERM_LOAN",
  "intakeSegment": "BORROWER",
  "requestedAmount": 100000,
  "personalInfo": {"fullName": "Clean Room UAT Borrower", "email": "cleanroom.uat@example.com", "mobile": "9999900001"}
}')
echo "$APP" | jdump | head -40
APP_ID=$(echo "$APP" | jget "d.get('id') or d.get('applicationId') or ''")
echo "APP_ID=$APP_ID"

echo "=== CATEGORY SELECTION (both eligible → disambiguation) ==="
curl -sS "${HDR[@]}" "$BASE/applications/$APP_ID/category-selection?allowDraftSimulation=true" | jdump | head -80

echo "=== ANSWER BANK_AA → expect BANK STARTER ==="
curl -sS "${HDR[@]}" -X POST "$BASE/applications/$APP_ID/category-selection/answers?allowDraftSimulation=true" -d '{
  "questionId": "FINANCIAL_DATA_ROUTE",
  "answerValue": "BANK_AA",
  "actor": "clean-room-uat",
  "actorRole": "RM"
}' | jdump | head -80

echo "=== HANDOFF ==="
HANDOFF=$(curl -sS "${HDR[@]}" "$BASE/applications/$APP_ID/category-selection/handoff")
echo "$HANDOFF" | jdump | head -60
POL_DOC=$(echo "$HANDOFF" | jget "d.get('selectedPolicyDocumentId') or d.get('policyDocumentId') or ''")
CAT_SEL=$(echo "$HANDOFF" | jget "d.get('selectedCustomerCategoryId') or d.get('customerCategoryId') or ''")
WF_SEL=$(echo "$HANDOFF" | jget "d.get('selectedWorkflowId') or d.get('workflowId') or ''")

echo "=== W4 PLAN-FROM-POLICY ==="
PLAN=$(curl -sS "${HDR[@]}" -X POST "$BASE/internal/requirement-plans/plan-from-policy" -d "{
  \"applicationId\": \"$APP_ID\",
  \"policyDocumentId\": \"$POL_DOC\",
  \"customerCategoryId\": \"$CAT_SEL\",
  \"workflowId\": \"$WF_SEL\",
  \"workflowVersion\": $WF_VER
}")
echo "$PLAN" | jdump | head -80
PLAN_ID=$(echo "$PLAN" | jget "d.get('id') or d.get('planId') or ''")

echo "=== W5 CUSTOMER REQUIREMENTS ==="
curl -sS "${HDR[@]}" "$BASE/applications/$APP_ID/customer-requirements" | jdump | head -80

if [[ -n "$PLAN_ID" && "$PLAN_ID" != "None" ]]; then
  echo "=== W6 ACQUIRE (dryRun) + COMPLETENESS GATE ==="
  curl -sS "${HDR[@]}" -X POST "$BASE/internal/requirement-plans/$PLAN_ID/acquire" -d '{"actor":"clean-room-uat","dryRun":true}' | jdump | head -60 || true
  curl -sS "${HDR[@]}" "$BASE/internal/requirement-plans/$PLAN_ID/completeness-gate" | jdump | head -60 || true
fi

echo "=== SHADOW POLICY TEST (no live UW) ==="
if [[ -n "$POL_DOC" && "$POL_DOC" != "None" ]]; then
  curl -sS "${HDR[@]}" -X POST "$SD/policy-studio/documents/$POL_DOC/test/application" -d "{\"applicationId\":\"$APP_ID\"}" | jdump | head -40 || true
fi

echo "=== SUMMARY IDS ==="
cat <<EOF
DOC_A=$DOC_A
APP_A=$APP_A
DOC_B=$DOC_B
APP_B=$APP_B
CAT_A_ID=$CAT_A_ID
CAT_B_ID=$CAT_B_ID
SC_ID=$SC_ID
APP_ID=$APP_ID
PLAN_ID=$PLAN_ID
WF_ID=$WF_ID
EOF
