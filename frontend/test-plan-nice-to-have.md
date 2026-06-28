# Test Plan: Nice-to-Have Features + Deployment Fixes

## What Changed
- 3 new frontend pages: Co-Lending (`/co-lending`), Collateral (`/collateral`), API Keys (`/admin/api-keys`)
- Dark mode toggle (Moon/Sun icon in Header, persists via localStorage)
- Sidebar updated with 3 new nav items (Co-Lending, Collateral, API Keys)
- API Gateway: 15 missing routes added (verified via YAML, no backend to test live routing)
- Dockerfiles + startup scripts + docker-compose.yml (verified via code review, no Docker build test needed in this session)

## Test Environment
- Frontend dev server at `http://localhost:3000` with mock data (no backend)
- All assertions based on hardcoded mock data in page components

---

## Test 1: Co-Lending Page — Data Rendering & Tab Switching
**Navigation**: Click "Co-Lending" in sidebar (icon: Handshake)

### Assertions:
1. **Page title** = "Co-Lending Management"
2. **Summary cards**: Active Partners = "3", Total Deals = "100", Avg Share % = "75%"
3. **Partners tab (default)**: Table shows 4 rows — "State Bank of India" (BANK, 80%, Active), "HDFC Bank" (BANK, 75%, Active), "Bajaj Finance" (NBFC, 70%, Active), "PNB Housing Finance" (HFC, 65%, Inactive with red badge)
4. **Click "allocations" tab**: Table shows 4 rows — first row has app# "LOS-IND-20260413-00001" with status "DISBURSED" (green badge)
5. **Click "settlement" tab**: Shows 3 partner cards (only active ones) — SBI card shows "Interest Spread: 0.5%", "Active Deals: 42"

**Distinguishes broken from working**: If the page component fails to render, the mock data won't appear. If tab switching is broken, the content won't change. The specific numeric values (3, 100, 75%, 0.5%) prove the correct mock data is being computed and displayed.

---

## Test 2: Collateral Page — Data Rendering & Type Filter
**Navigation**: Click "Collateral" in sidebar (icon: Building2)

### Assertions:
1. **Page title** = "Collateral Valuation"
2. **Summary cards**: Total Collaterals = "6", Completed = "5/6"
3. **Default view**: Shows 6 collateral cards with emoji icons (🏠, ⚙️, 🚗, 🥇, 🏠, 🏦)
4. **Select "Vehicle" from type dropdown**: Shows only 1 card — "2024 Toyota Fortuner Legender" with Market Value "₹42,00,000"
5. **Select "All Types"**: Returns to showing 6 cards

**Distinguishes broken from working**: The type filter dropdown controls rendering. If filtering is broken, the card count won't change from 6 to 1 when "Vehicle" is selected.

---

## Test 3: API Keys Page — Tabs & Generate Form Toggle
**Navigation**: Click "API Keys" in sidebar under admin section (icon: Key)

### Assertions:
1. **Page title** = "API Key Management"
2. **Summary cards**: Total Keys = "4", Active Keys = "3" (green text), Whitelisted IPs = "4"
3. **Keys tab (default)**: Shows 4 key cards — "QuickLoan DSA" has red "Revoked" badge and red-tinted border
4. **Click "Generate API Key" button**: Blue form appears with "Partner Name", "Description" fields and "Production/Sandbox" dropdown
5. **Click "Cancel" in form**: Form disappears
6. **Click "IP Whitelist" tab**: Shows table with 4 rows — first row has IP "10.0.0.0/8" with description "Internal network"

**Distinguishes broken from working**: The form toggle (show/hide) proves state management works. The revoked key's distinct red styling proves conditional rendering.

---

## Test 4: Dark Mode Toggle
**Navigation**: Click Moon icon in header (right side, next to Bell icon)

### Assertions:
1. **Before toggle**: Moon icon visible, page background is light (white/gray)
2. **Click Moon icon**: Background changes to dark color scheme, icon changes to Sun
3. **Navigate to another page** (e.g., Dashboard): Dark mode persists across navigation
4. **Click Sun icon**: Returns to light mode, icon changes back to Moon

**Distinguishes broken from working**: If the toggle doesn't add/remove `dark` class on `<html>`, the CSS variables won't switch and the background color won't change visually.

---

## Test 5: Sidebar Navigation — New Items Present
**Navigation**: Observe sidebar

### Assertions:
1. Sidebar contains "Co-Lending" nav item (between Transactions and Notifications)
2. Sidebar contains "Collateral" nav item (after Co-Lending)
3. Sidebar contains "API Keys" nav item (after Aggregators, in admin section)
4. Clicking each highlights it with primary color background

**Distinguishes broken from working**: If the sidebar items are missing, you can't navigate to the new pages at all.
