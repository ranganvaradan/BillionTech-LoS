import { useEffect, useMemo, useState } from 'react'
import { fetchGeoCitiesForState, type GeoStateRow } from '@/api/geoMaster'
import { indiaStateIdentity } from '@/lib/intake/indiaStateIdentity'
import { ensureGeoStatesLoaded, syncCitiesForStateName } from '@/lib/intake/masterGeoClientCache'

const INPUT_CLASS = 'bt-input w-full'
const SELECT_CLASS = 'bt-input w-full bg-white'

export interface IndiaStateCityPincodeFieldsProps {
  stateValue: string
  cityValue: string
  pincodeValue: string
  onStateChange: (state: string) => void
  onCityChange: (city: string) => void
  onPincodeChange: (pincode: string) => void
  /** When false, city select stays disabled until a state is chosen. */
  cityDisabledUntilState?: boolean
  /** When true, state/city labels show * (borrower address step). */
  stateCityRequired?: boolean
  /**
   * When set (non-empty), only these geo-master state names appear in the dropdown.
   * Null / undefined / empty = all states (default).
   */
  allowedStates?: string[] | null
}

function matchStateName(states: readonly GeoStateRow[], stateValue: string): GeoStateRow | undefined {
  const identity = indiaStateIdentity(stateValue)
  if (!identity) return undefined
  return states.find(
    (r) =>
      indiaStateIdentity(r.stateName) === identity || indiaStateIdentity(r.stateCode) === identity,
  )
}

/**
 * State + dependent city + PIN (6-digit) using the same grid styling as ApplicationIntakeWizard.
 * Master lists load from LOS APIs; persisted free-text legacy values remain selectable until corrected.
 */
export function IndiaStateCityPincodeFields({
  stateValue,
  cityValue,
  pincodeValue,
  onStateChange,
  onCityChange,
  onPincodeChange,
  cityDisabledUntilState = true,
  stateCityRequired = false,
  allowedStates = null,
}: IndiaStateCityPincodeFieldsProps) {
  const [geoStates, setGeoStates] = useState<GeoStateRow[]>([])
  const [statesLoadErr, setStatesLoadErr] = useState<string | null>(null)
  const [citiesLocal, setCitiesLocal] = useState<string[]>([])
  const [citiesLoadErr, setCitiesLoadErr] = useState<string | null>(null)
  const [loadingStates, setLoadingStates] = useState(true)
  const [loadingCities, setLoadingCities] = useState(false)

  useEffect(() => {
    let c = true
    setLoadingStates(true)
    setStatesLoadErr(null)
    ensureGeoStatesLoaded()
      .then((rows) => {
        if (c) setGeoStates([...rows])
      })
      .catch(() => {
        if (c) setStatesLoadErr('Unable to load states. Retry after checking your connection.')
      })
      .finally(() => {
        if (c) setLoadingStates(false)
      })
    return () => {
      c = false
    }
  }, [])

  const filteredGeoStates = useMemo(() => {
    if (!allowedStates || allowedStates.length === 0) return geoStates
    const allow = allowedStates.map((s) => indiaStateIdentity(s)).filter(Boolean)
    if (allow.length === 0) return geoStates
    return geoStates.filter((r) => {
      const nameId = indiaStateIdentity(r.stateName)
      const codeId = indiaStateIdentity(r.stateCode)
      return allow.some((a) => a === nameId || a === codeId)
    })
  }, [geoStates, allowedStates])

  const matchedMasterState = useMemo(
    () => matchStateName(filteredGeoStates, stateValue) ?? matchStateName(geoStates, stateValue),
    [filteredGeoStates, geoStates, stateValue],
  )

  const stateTrim = stateValue.trim()
  const canonicalNames = filteredGeoStates.map((r) => r.stateName)
  const inAllowedList =
    !allowedStates ||
    allowedStates.length === 0 ||
    allowedStates.some((s) => indiaStateIdentity(s) === indiaStateIdentity(stateTrim))
  const stateUnknown = Boolean(stateTrim && (!matchedMasterState || !inAllowedList))

  useEffect(() => {
    let cancel = false
    if (!matchedMasterState?.id) {
      setCitiesLocal([])
      setCitiesLoadErr(null)
      setLoadingCities(false)
      return
    }
    setLoadingCities(true)
    setCitiesLoadErr(null)
    fetchGeoCitiesForState(matchedMasterState.id)
      .then((rows) => {
        const names = rows.map((r) => r.cityName).sort((a, b) => a.localeCompare(b))
        if (cancel) return
        setCitiesLocal(names)
        syncCitiesForStateName(matchedMasterState.stateName, names)
      })
      .catch(() => {
        if (!cancel) {
          setCitiesLocal([])
          setCitiesLoadErr('Unable to load cities for this state.')
        }
      })
      .finally(() => {
        if (!cancel) setLoadingCities(false)
      })
    return () => {
      cancel = true
    }
  }, [matchedMasterState?.id, matchedMasterState?.stateName])

  const cities = matchedMasterState ? citiesLocal : []
  const cityDisabled = Boolean(cityDisabledUntilState && !stateValue.trim()) || loadingStates
  const cityUnknown = Boolean(cityValue.trim() && stateValue.trim() && !cities.includes(cityValue.trim()))

  return (
    <>
      {(statesLoadErr || citiesLoadErr) && !(loadingStates || loadingCities) ? (
        <p className="text-xs text-amber-800 sm:col-span-2">{statesLoadErr ?? citiesLoadErr}</p>
      ) : null}
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">State{stateCityRequired ? ' *' : ''}</span>
        <select
          className={SELECT_CLASS}
          value={stateUnknown ? stateTrim : stateValue}
          onChange={(e) => onStateChange(e.target.value)}
          disabled={loadingStates}
          aria-label="State"
          aria-busy={loadingStates}
        >
          <option value="">{loadingStates ? 'Loading states…' : 'Select state'}</option>
          {stateUnknown ? (
            <option value={stateTrim}>
              {stateTrim} (saved value — pick a standard state to update)
            </option>
          ) : null}
          {canonicalNames.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">City{stateCityRequired ? ' *' : ''}</span>
        <select
          className={SELECT_CLASS}
          value={cityUnknown ? cityValue.trim() : cityValue}
          disabled={cityDisabled || loadingCities || Boolean(statesLoadErr)}
          onChange={(e) => onCityChange(e.target.value)}
          aria-label="City"
          aria-busy={loadingCities}
        >
          <option value="">
            {cityDisabled ? 'Select state first' : loadingCities ? 'Loading cities…' : 'Select city'}
          </option>
          {cityUnknown ? (
            <option value={cityValue.trim()}>
              {cityValue.trim()} (saved value — pick a standard city to update)
            </option>
          ) : null}
          {cities.map((cname) => (
            <option key={cname} value={cname}>
              {cname}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-sm text-slate-700">
        <span className="mb-1 block text-xs font-medium text-slate-500">PIN code *</span>
        <input
          type="text"
          className={`${INPUT_CLASS} tabular-nums`}
          value={pincodeValue}
          onChange={(e) => onPincodeChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
          maxLength={6}
          inputMode="numeric"
          autoComplete="postal-code"
          placeholder="6-digit PIN"
          aria-label="PIN code"
        />
      </label>
    </>
  )
}
