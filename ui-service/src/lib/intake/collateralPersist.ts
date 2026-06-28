import { listDocuments } from '@/api/documents'
import { updateApplication } from '@/api/applications'
import { buildBorrowerIntakeCollateralPayload } from './collateralIntakePayload'
import type { IntakeFormState, IntakeMode } from './intakeTypes'
import { collateralDocumentTypesForKind, detectSecuredCollateralKind } from './securedProducts'

export async function persistBorrowerIntakeCollateral(
  applicationId: string,
  form: IntakeFormState,
  mode: IntakeMode,
): Promise<void> {
  const kind = detectSecuredCollateralKind(form.loanProduct)
  if (!kind) return
  const docs = await listDocuments(applicationId)
  const want = new Set(collateralDocumentTypesForKind(kind))
  const ids = docs.filter((d) => want.has(d.documentType)).map((d) => d.id)
  const payload = buildBorrowerIntakeCollateralPayload(form, mode, ids)
  await updateApplication(applicationId, { collateralInfo: payload })
}
