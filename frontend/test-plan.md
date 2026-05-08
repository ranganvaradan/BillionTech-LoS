# LOS Platform v2.0 — Frontend Test Plan

## What Changed
Phase 4 adds a complete Next.js 16 frontend with 10 routes: Login, Dashboard, Applications (list/detail/new), KYC Management, Workflows, and Transactions. All pages use mock data fallback when backend APIs are unavailable.

## What We Will Test
One end-to-end flow covering the primary user journey: Dashboard → Applications List → New Application Wizard → Application Detail, plus verifying sidebar navigation, KYC, Workflows, and Transactions pages render correctly.

## Test Environment
- Local dev server at `http://localhost:3000`
- Backend NOT running — all pages use mock data fallback
- Next.js 16.2.3 + Turbopack

---

## Test 1: Dashboard renders with correct mock data
**Steps:**
1. Navigate to `http://localhost:3000/dashboard`
2. Verify page renders (not blank/error)

**Assertions:**
- Page title text "Dashboard" is visible
- Stat card "Total Applications" shows value `156`
- Stat card "Today" shows value `8`
- Application pipeline section shows status grid with "Approved" count of `22` and "Disbursed" count of `38`
- Recent Applications table shows at least 5 rows
- First row application number is `LOS-IND-20260413-00001`
- "+ New Application" button is visible in top-right area

---

## Test 2: Sidebar navigation works across all pages
**Steps:**
1. From Dashboard, click "Applications" in sidebar
2. Verify Applications list page loads
3. Click "KYC Management" in sidebar
4. Click "Workflows" in sidebar
5. Click "Transactions" in sidebar

**Assertions:**
- Each page renders with its correct heading:
  - Applications → heading "Loan Applications"
  - KYC → heading "KYC Management"
  - Workflows → heading "KYC Workflows"
  - Transactions → heading "Transactions"
- Active sidebar item is highlighted (blue background) for the current page

---

## Test 3: Applications list shows mock data with filtering
**Steps:**
1. Navigate to `/applications`
2. Verify table shows 8 rows of mock data
3. Type "Business" in the search input
4. Verify filtered results

**Assertions:**
- Table header columns include "Application #", "Borrower Type", "Product", "Requested", "Status"
- Before filter: 8 rows visible
- After typing "Business": only rows with "Business Loan" product remain (3 rows: ids 2,3,6)
- Each row has a colored status badge

---

## Test 4: New Application wizard — complete 4-step flow
**Steps:**
1. Click "+ New Application" button from dashboard or applications page
2. Step 1 (Borrower Info): Select "Individual", fill Full Name = "Test User", Mobile = "9876543210"
3. Click "Next"
4. Step 2 (Loan Details): Select "Term Loan", enter Requested Amount = "500000"
5. Click "Next"
6. Step 3 (Financial Info): Enter Monthly Income = "75000"
7. Click "Next"
8. Step 4 (Review): Verify summary data

**Assertions:**
- Step indicator shows 4 steps with proper progression (step 1 active → step 2 active → etc.)
- "Next" button is disabled when required fields empty, enabled when filled
- Review page shows: Name = "Test User", Type = "INDIVIDUAL", Product = "Term Loan", Amount = "₹5,00,000"
- "Submit Application" button is green and visible on step 4

---

## Test 5: Application detail page — tabbed view
**Steps:**
1. Navigate to `/applications/1`
2. Verify detail page renders with mock data
3. Click each tab: KYC, Documents, Credit Decision, Transactions, Audit Trail

**Assertions:**
- Header shows application number `LOS-IND-20260413-00001` with status badge "KYC In Progress"
- Summary cards show: Requested = ₹5,00,000, Interest Rate = 12.5%, Tenure = 36 months
- Info tab shows Personal Information section with "fullName", "email", "mobile" fields
- Each tab click changes content area (KYC shows "No KYC steps executed yet", Documents shows "No documents uploaded yet", etc.)
- Status transition buttons are visible (valid next states for KYC_IN_PROGRESS)

---

## Test 6: Login page renders correctly
**Steps:**
1. Navigate to `/login`

**Assertions:**
- "BillionTech LOS" logo/title is visible
- "Loan Origination System v2.0" subtitle is visible
- Username and Password fields are present
- "Sign In" button is visible
- Default credentials hint "admin / Admin@LOS2026" is shown at bottom

---

## Test 7: Sidebar collapse toggle
**Steps:**
1. From dashboard, click the collapse button at bottom of sidebar (chevron left icon)
2. Verify sidebar collapses to icon-only mode
3. Click again to expand

**Assertions:**
- Before collapse: sidebar shows text labels ("Dashboard", "Applications", etc.) alongside icons
- After collapse: sidebar narrows, only icons visible, text labels hidden
- After re-expand: text labels reappear
