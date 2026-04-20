# Test Plan: Notifications & Reports Pages (Phase 5 Balance Features)

## What Changed
Two new frontend routes added to the LOS platform:
- `/notifications` — SMS/Email/WhatsApp notification history with search, channel filter, status filter, summary cards, and resend button for failed items
- `/reports` — 3-tab reporting page (Dashboard Analytics, MIS Report, Regulatory Compliance) with mock data

Both are accessible via new sidebar nav items (Bell icon → Notifications, BarChart3 icon → Reports).

## Evidence (code paths that informed this plan)
- `frontend/src/components/layout/Sidebar.tsx` lines 26-27: nav items for `/notifications` and `/reports`
- `frontend/src/app/notifications/page.tsx` lines 22-33: 10 mock notification items with specific statuses
- `frontend/src/app/notifications/page.tsx` lines 59-68: filtering logic (channel, status, search term)
- `frontend/src/app/notifications/page.tsx` lines 71-74: summary card computations
- `frontend/src/app/notifications/page.tsx` lines 184-188: Resend button only for FAILED/PERMANENTLY_FAILED
- `frontend/src/app/reports/page.tsx` lines 97-98: 3 tabs (analytics, mis, regulatory)
- `frontend/src/app/reports/page.tsx` lines 152-157: Analytics metric cards with specific values
- `frontend/src/app/reports/page.tsx` lines 244-265: MIS summary cards with specific values
- `frontend/src/app/reports/page.tsx` lines 331-336: RBI circular reference text

---

## Test 1: Notifications Page — Rendering & Summary Cards
**Navigate**: Click "Notifications" in sidebar
**Assertions**:
1. Page title is "Notifications" with subtitle "SMS, Email & WhatsApp notification history and management"
2. Summary cards show exactly: Total=10, Sent=7, Failed=2, Success Rate=70.0%
3. Table header row shows "10 notifications" count text
4. Table has 7 columns: Channel, Recipient, Application, Event, Status, Sent At, Actions

## Test 2: Notifications Page — Channel Filter (WHATSAPP)
**Action**: Select "WhatsApp" from channel filter dropdown
**Assertions**:
1. Count text changes to "2 notifications"
2. Only 2 rows visible — both showing WHATSAPP channel icon+label
3. Application numbers are "LOS-IND-20260413-00001" and "LOS-IND-20260410-00005"

## Test 3: Notifications Page — Status Filter (FAILED) & Resend Button
**Action**: Reset channel filter to "All Channels", then select "Failed" from status filter
**Assertions**:
1. Count text changes to "1 notification" (singular)
2. Single row shows: EMAIL channel, recipient "rahul.sharma@gmail.com", status badge "Failed"
3. Error message "SMTP connection timeout" visible below the status badge
4. "Resend" button is visible in the Actions column

## Test 4: Notifications Page — Search Filter
**Action**: Clear status filter, type "Rahul" in search box
**Assertions**:
1. Count text changes to "2 notifications"
2. Both rows show borrower name "Rahul Sharma"

## Test 5: Reports Page — Analytics Tab (Default)
**Navigate**: Click "Reports" in sidebar
**Assertions**:
1. Page title is "Reports" with subtitle "Analytics, MIS reports, and regulatory compliance"
2. "Export Report" button visible in top-right
3. "Dashboard Analytics" tab is active (highlighted)
4. Four metric cards show: Approval Rate=25.6%, Conversion Rate=11.5%, Avg Loan Size (₹ value), Total Disbursed (₹ value)
5. "Application Status Distribution" section has 9 status items including DRAFT (35), KYC_IN_PROGRESS (28), DISBURSED (18)
6. Monthly Trends table has 6 rows (Nov 2025 through Apr 2026)

## Test 6: Reports Page — MIS Tab
**Action**: Click "MIS Report" tab
**Assertions**:
1. Tab switches — MIS content visible
2. Five summary cards: 156 Total Applications, 40 Approved, 8 Rejected, 18 Disbursed, 90 In Pipeline
3. "Recent Applications (Top 10)" table visible with 5 data rows
4. First row shows "LOS-IND-20260413-00001" / "Amit Patel" / "Business Loan"

## Test 7: Reports Page — Regulatory Compliance Tab
**Action**: Click "Regulatory Compliance" tab
**Assertions**:
1. Blue banner shows "RBI Digital Lending Guidelines Compliance"
2. RBI circular reference text "RBI/2022-23/111 DOR.FIN.REC.66/03.10.038/2022-23" visible
3. "Digital Lending Compliance" section shows KFS Issued=30, eSign Completed=18, Overall Compliance=100%
4. "KYC Compliance" section shows Completion Rate=74.3%
5. "DPD Analysis" section shows NPA %=0%

## Test 8 (Regression): Sidebar Navigation — New Items Present
**Action**: Verify sidebar shows both new nav items
**Assertions**:
1. Sidebar shows "Notifications" with Bell icon between "Transactions" and "Reports"
2. Sidebar shows "Reports" with chart icon as last nav item
3. Active page highlighting works correctly on both new pages
