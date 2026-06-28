# Manual Smoke Scenarios

## Scenario 1: Happy path application
- Create application
- Submit application
- Complete KYC
- Run underwriting
- Approve
- Issue sanction
- Complete fulfillment step
- Mark disbursed

## Scenario 2: KYC fail path
- Create application
- Trigger mandatory KYC fail
- Confirm application blocks appropriately
- Confirm KYC summary reflects failure correctly

## Scenario 3: Manual KYC review path
- Create application
- Force a manual review condition
- Apply override
- Confirm effective decision changes as expected

## Scenario 4: Underwriting reject path
- Pass KYC
- Trigger reject case in underwriting
- Confirm lifecycle and UI both show correct final state
