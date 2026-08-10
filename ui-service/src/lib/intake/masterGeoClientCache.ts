import { fetchGeoCitiesForState, fetchGeoStates, type GeoStateRow } from '@/api/geoMaster'
import type { IntakeFormState } from '@/lib/intake/intakeTypes'

let cachedStates: GeoStateRow[] | null = null
let statesLoadPromise: Promise<GeoStateRow[]> | null = null

const citiesByCanonicalStateName = new Map<string, string[]>()

function canonicalStateKey(name: string): string {
  return name.trim().toLowerCase()
}

/** For unit tests only — resets in-memory master cache. */
export function resetGeoMasterClientCache(): void {
  cachedStates = null
  statesLoadPromise = null
  citiesByCanonicalStateName.clear()
}

/** For unit tests only — prime cache without calling the API. */
export function seedGeoMasterClientCacheForTests(
  states: GeoStateRow[],
  citiesByStateName: Record<string, string[]>,
): void {
  cachedStates = [...states]
  citiesByCanonicalStateName.clear()
  for (const [stateName, list] of Object.entries(citiesByStateName)) {
    const label = states.find((s) => s.stateName.toLowerCase() === stateName.trim().toLowerCase())?.stateName ?? stateName
    if (!label.trim()) continue
    citiesByCanonicalStateName.set(canonicalStateKey(label.trim()), [...list])
  }
}

export async function ensureGeoStatesLoaded(): Promise<GeoStateRow[]> {
  if (cachedStates) return cachedStates
  if (statesLoadPromise) return statesLoadPromise
  statesLoadPromise = fetchGeoStates()
    .then((rows) => {
      cachedStates = rows
      return rows
    })
    .finally(() => {
      statesLoadPromise = null
    })
  return statesLoadPromise
}

export function getGeoStatesSnapshot(): readonly GeoStateRow[] {
  return cachedStates ?? []
}

/** Called when dropdowns load cities from the wire so validations stay aligned. */
export function syncCitiesForStateName(stateName: string, cityNames: readonly string[]): void {
  const row = findGeoStateRowByName(stateName)
  const label = row?.stateName ?? stateName.trim()
  if (!label) return
  citiesByCanonicalStateName.set(canonicalStateKey(label), [...cityNames])
}

export function findGeoStateRowByName(stateName: string): GeoStateRow | undefined {
  const k = canonicalStateKey(stateName)
  return getGeoStatesSnapshot().find((r) => canonicalStateKey(r.stateName) === k)
}

export function listIndianStates(): readonly string[] {
  return getGeoStatesSnapshot().map((r) => r.stateName)
}

export function listCitiesForIndianState(stateName: string): readonly string[] {
  const row = findGeoStateRowByName(stateName)
  if (!row) return []
  return citiesByCanonicalStateName.get(canonicalStateKey(row.stateName)) ?? []
}

export function isKnownIndianState(stateName: string): boolean {
  return findGeoStateRowByName(stateName) !== undefined
}

export function isCityInIndianState(stateName: string, cityName: string): boolean {
  const ct = cityName.trim()
  if (!ct || !stateName.trim()) return false
  return listCitiesForIndianState(stateName).includes(ct)
}

/**
 * Hydrates validation cache when the user selects a canonical state from master.
 */
export async function ensureCitiesLoadedForStateName(stateName: string): Promise<void> {
  const row = findGeoStateRowByName(stateName.trim())
  if (!row?.id) return
  const names = await fetchGeoCitiesForState(row.id).then((cities) => cities.map((c) => c.cityName))
  syncCitiesForStateName(row.stateName, names)
}

export async function prefetchIntakeGeoForValidation(form: IntakeFormState): Promise<void> {
  await ensureGeoStatesLoaded()
  if (form.borrowerType === 'INDIVIDUAL') {
    await ensureCitiesLoadedForStateName(form.state)
  } else {
    await ensureCitiesLoadedForStateName(form.businessState)
  }
}
