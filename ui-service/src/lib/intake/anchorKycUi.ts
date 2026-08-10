/** KYC workflow steps that run server-side for anchors but are hidden on the Anchor KYC tab. */
export const ANCHOR_HIDDEN_KYC_UI_STEPS = new Set(['AML_SCREENING', 'CIN_MCA21'])

export function isAnchorHiddenKycUiStep(stepName: string): boolean {
  return ANCHOR_HIDDEN_KYC_UI_STEPS.has(stepName.trim().toUpperCase())
}

export function filterAnchorKycUiSteps<T extends { stepType: string }>(
  isAnchor: boolean,
  rows: readonly T[],
): T[] {
  if (!isAnchor) return [...rows]
  return rows.filter((r) => !isAnchorHiddenKycUiStep(r.stepType))
}

export function filterAnchorKycUiStepNames(isAnchor: boolean, stepNames: readonly string[]): string[] {
  if (!isAnchor) return [...stepNames]
  return stepNames.filter((name) => !isAnchorHiddenKycUiStep(name))
}
