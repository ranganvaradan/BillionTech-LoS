import type { IntakeFormState } from '@/lib/intake/intakeTypes'
import { COLLATERAL_DOC, collateralDocumentTypesForKind, type SecuredCollateralKind } from '@/lib/intake/securedProducts'
import type { Dispatch, SetStateAction } from 'react'

export function CollateralIntakeFields({
  kind,
  form,
  setForm,
  applicationId,
  onUploadFile,
  busy,
  documentWarning,
}: {
  kind: SecuredCollateralKind
  form: IntakeFormState
  setForm: Dispatch<SetStateAction<IntakeFormState>>
  applicationId: string | null
  onUploadFile: (documentType: string, file: File | null) => void
  busy: boolean
  documentWarning: string | null
}) {
  const docTypes = collateralDocumentTypesForKind(kind)
  return (
    <div className="space-y-4">
      {documentWarning ? (
        <p className="bt-alert bt-alert-warning">{documentWarning}</p>
      ) : null}
      {kind === 'PROPERTY' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Property type *</span>
            <input
              className="bt-input w-full"
              value={form.collateralPropertyType}
              onChange={(e) => setForm((f) => ({ ...f, collateralPropertyType: e.target.value }))}
              placeholder="e.g. residential flat, land, commercial"
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Property address *</span>
            <textarea
              className="bt-input w-full"
              rows={3}
              value={form.collateralPropertyAddress}
              onChange={(e) => setForm((f) => ({ ...f, collateralPropertyAddress: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Ownership type *</span>
            <input
              className="bt-input w-full"
              value={form.collateralOwnershipType}
              onChange={(e) => setForm((f) => ({ ...f, collateralOwnershipType: e.target.value }))}
              placeholder="e.g. sole, joint, leasehold"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Estimated market value (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralEstimatedMarketValue}
              onChange={(e) => setForm((f) => ({ ...f, collateralEstimatedMarketValue: e.target.value }))}
            />
          </label>
          <div className="sm:col-span-2">
            <span className="mb-2 block text-xs font-medium text-slate-500">Existing mortgage / encumbrance? *</span>
            <div className="flex flex-wrap gap-4 text-sm text-slate-800">
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="mort"
                  checked={form.collateralExistingMortgage === 'no'}
                  onChange={() => setForm((f) => ({ ...f, collateralExistingMortgage: 'no' }))}
                />
                No
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="mort"
                  checked={form.collateralExistingMortgage === 'yes'}
                  onChange={() => setForm((f) => ({ ...f, collateralExistingMortgage: 'yes' }))}
                />
                Yes
              </label>
            </div>
          </div>
        </div>
      ) : null}
      {kind === 'SHARES' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Security type *</span>
            <input
              className="bt-input w-full"
              value={form.collateralSecurityType}
              onChange={(e) => setForm((f) => ({ ...f, collateralSecurityType: e.target.value }))}
              placeholder="e.g. equity, debt MF"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">ISIN *</span>
            <input
              className="bt-input w-full font-mono uppercase"
              value={form.collateralIsin}
              onChange={(e) => setForm((f) => ({ ...f, collateralIsin: e.target.value.toUpperCase() }))}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Company / mutual fund name *</span>
            <input
              className="bt-input w-full"
              value={form.collateralCompanyOrFundName}
              onChange={(e) => setForm((f) => ({ ...f, collateralCompanyOrFundName: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Quantity *</span>
            <input
              className="bt-input w-full"
              value={form.collateralShareQuantity}
              onChange={(e) => setForm((f) => ({ ...f, collateralShareQuantity: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Current market value (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralShareMarketValue}
              onChange={(e) => setForm((f) => ({ ...f, collateralShareMarketValue: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Demat account number *</span>
            <input
              className="bt-input w-full font-mono"
              value={form.collateralDematAccountNumber}
              onChange={(e) => setForm((f) => ({ ...f, collateralDematAccountNumber: e.target.value }))}
            />
          </label>
          <label className="flex items-start gap-2 text-sm text-slate-800 sm:col-span-2">
            <input
              type="checkbox"
              className="mt-1"
              checked={form.collateralPledgeConsent}
              onChange={(e) => setForm((f) => ({ ...f, collateralPledgeConsent: e.target.checked }))}
            />
            I consent to creation of a pledge on these demat / securities for this loan. *
          </label>
        </div>
      ) : null}
      {kind === 'GOLD' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Gold / item type *</span>
            <input
              className="bt-input w-full"
              value={form.collateralGoldType}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldType: e.target.value }))}
              placeholder="e.g. jewellery, bar, coin"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Estimated value (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralGoldEstimatedValue}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldEstimatedValue: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Approx. gross weight (g)</span>
            <input
              className="bt-input w-full"
              value={form.collateralGoldGrossWeight}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldGrossWeight: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Approx. net weight (g)</span>
            <input
              className="bt-input w-full"
              value={form.collateralGoldNetWeight}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldNetWeight: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Purity / karat *</span>
            <input
              className="w-full max-w-md bt-input"
              value={form.collateralGoldPurityKarat}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldPurityKarat: e.target.value }))}
              placeholder="e.g. 22K"
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Description of ornament / item(s) *</span>
            <textarea
              className="bt-input w-full"
              rows={2}
              value={form.collateralGoldOrnamentDescription}
              onChange={(e) => setForm((f) => ({ ...f, collateralGoldOrnamentDescription: e.target.value }))}
            />
          </label>
        </div>
      ) : null}
      {kind === 'VEHICLE' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Vehicle type *</span>
            <select
              className="bt-input w-full"
              value={form.collateralVehicleType}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  collateralVehicleType: e.target.value as IntakeFormState['collateralVehicleType'],
                }))
              }
            >
              <option value="">Select…</option>
              <option value="TWO_WHEELER">Two wheeler</option>
              <option value="FOUR_WHEELER">Four wheeler</option>
              <option value="COMMERCIAL">Commercial</option>
            </select>
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Make / model *</span>
            <input
              className="bt-input w-full"
              value={form.collateralVehicleMakeModel}
              onChange={(e) => setForm((f) => ({ ...f, collateralVehicleMakeModel: e.target.value }))}
              placeholder="e.g. Maruti Swift VXI"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Year of manufacture *</span>
            <input
              className="bt-input w-full tabular-nums"
              value={form.collateralVehicleYear}
              onChange={(e) => setForm((f) => ({ ...f, collateralVehicleYear: e.target.value.replace(/\D/g, '').slice(0, 4) }))}
              inputMode="numeric"
              placeholder="e.g. 2022"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Registration number *</span>
            <input
              className="bt-input w-full font-mono uppercase"
              value={form.collateralVehicleRegistrationNumber}
              onChange={(e) =>
                setForm((f) => ({ ...f, collateralVehicleRegistrationNumber: e.target.value.toUpperCase() }))
              }
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Estimated market value (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralVehicleEstimatedMarketValue}
              onChange={(e) => setForm((f) => ({ ...f, collateralVehicleEstimatedMarketValue: e.target.value }))}
            />
          </label>
          <div className="sm:col-span-2">
            <span className="mb-2 block text-xs font-medium text-slate-500">Existing loan on vehicle? *</span>
            <div className="flex flex-wrap gap-4 text-sm text-slate-800">
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="vehicleLoan"
                  checked={form.collateralVehicleExistingLoan === 'no'}
                  onChange={() => setForm((f) => ({ ...f, collateralVehicleExistingLoan: 'no' }))}
                />
                No
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="radio"
                  name="vehicleLoan"
                  checked={form.collateralVehicleExistingLoan === 'yes'}
                  onChange={() => setForm((f) => ({ ...f, collateralVehicleExistingLoan: 'yes' }))}
                />
                Yes
              </label>
            </div>
          </div>
        </div>
      ) : null}
      {kind === 'FIXED_DEPOSIT' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Bank name *</span>
            <input
              className="bt-input w-full"
              value={form.collateralFdBankName}
              onChange={(e) => setForm((f) => ({ ...f, collateralFdBankName: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">FD account number *</span>
            <input
              className="bt-input w-full font-mono"
              value={form.collateralFdAccountNumber}
              onChange={(e) => setForm((f) => ({ ...f, collateralFdAccountNumber: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">FD amount (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralFdAmount}
              onChange={(e) => setForm((f) => ({ ...f, collateralFdAmount: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Maturity date *</span>
            <input
              type="date"
              className="bt-input w-full"
              value={form.collateralFdMaturityDate}
              onChange={(e) => setForm((f) => ({ ...f, collateralFdMaturityDate: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">FD receipt number *</span>
            <input
              className="bt-input w-full font-mono"
              value={form.collateralFdReceiptNumber}
              onChange={(e) => setForm((f) => ({ ...f, collateralFdReceiptNumber: e.target.value }))}
            />
          </label>
        </div>
      ) : null}
      {kind === 'MACHINERY' ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Machinery type / description *</span>
            <input
              className="bt-input w-full"
              value={form.collateralMachineryTypeDescription}
              onChange={(e) => setForm((f) => ({ ...f, collateralMachineryTypeDescription: e.target.value }))}
              placeholder="e.g. CNC lathe, printing press"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Make / model *</span>
            <input
              className="bt-input w-full"
              value={form.collateralMachineryMakeModel}
              onChange={(e) => setForm((f) => ({ ...f, collateralMachineryMakeModel: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Year of purchase *</span>
            <input
              className="bt-input w-full tabular-nums"
              value={form.collateralMachineryYearOfPurchase}
              onChange={(e) =>
                setForm((f) => ({ ...f, collateralMachineryYearOfPurchase: e.target.value.replace(/\D/g, '').slice(0, 4) }))
              }
              inputMode="numeric"
              placeholder="e.g. 2019"
            />
          </label>
          <label className="block text-sm text-slate-700">
            <span className="mb-1 block text-xs font-medium text-slate-500">Estimated current value (INR) *</span>
            <input
              type="number"
              min={0}
              step="1"
              className="bt-input w-full tabular-nums"
              value={form.collateralMachineryEstimatedValue}
              onChange={(e) => setForm((f) => ({ ...f, collateralMachineryEstimatedValue: e.target.value }))}
            />
          </label>
          <label className="block text-sm text-slate-700 sm:col-span-2">
            <span className="mb-1 block text-xs font-medium text-slate-500">Location / address *</span>
            <textarea
              className="bt-input w-full"
              rows={2}
              value={form.collateralMachineryLocationAddress}
              onChange={(e) => setForm((f) => ({ ...f, collateralMachineryLocationAddress: e.target.value }))}
            />
          </label>
        </div>
      ) : null}

      <div className="rounded border border-slate-100 bg-slate-50/80 p-4">
        <div className="text-sm font-medium text-slate-900">Collateral documents</div>
        <p className="text-xs text-slate-600">Upload what you have now; a valuation is helpful but optional in demo mode.</p>
        <ul className="mt-3 space-y-3">
          {docTypes.map((dt) => (
            <li key={dt} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="min-w-0 flex-1 text-slate-800">{labelForDocType(dt)}</span>
              <input
                type="file"
                accept=".pdf,image/*"
                className="text-xs"
                disabled={busy || !applicationId}
                onChange={(e) => {
                  const file = e.target.files?.[0] ?? null
                  if (e.target) e.target.value = ''
                  void onUploadFile(dt, file)
                }}
              />
              {form.documentUploaded[dt] ? <span className="text-xs font-medium text-emerald-800">Received</span> : null}
            </li>
          ))}
        </ul>
        {!applicationId ? <p className="mt-2 text-xs text-amber-800">Save the previous step first, then you can upload files.</p> : null}
      </div>
    </div>
  )
}

function labelForDocType(code: string): string {
  if (code === COLLATERAL_DOC.PROPERTY_DOCUMENT) return 'Property document (title / deed / agreement)'
  if (code === COLLATERAL_DOC.PROPERTY_VALUATION) return 'Valuation report (if available)'
  if (code === COLLATERAL_DOC.SHARE_HOLDING_STATEMENT) return 'Holding / demat statement'
  if (code === COLLATERAL_DOC.GOLD_PHOTO) return 'Photo of gold / security item(s)'
  if (code === COLLATERAL_DOC.GOLD_VALUATION) return 'Valuation (if available)'
  if (code === COLLATERAL_DOC.COLLATERAL_OTHER) return 'Other supporting document'
  return code
}
