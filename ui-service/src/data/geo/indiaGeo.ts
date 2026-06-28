/** Barrel for validations & legacy imports — geo data is loaded from LOS master APIs. */
export {
  ensureGeoStatesLoaded,
  ensureCitiesLoadedForStateName,
  findGeoStateRowByName,
  getGeoStatesSnapshot,
  prefetchIntakeGeoForValidation,
  resetGeoMasterClientCache,
  seedGeoMasterClientCacheForTests,
  syncCitiesForStateName,
  isCityInIndianState,
  isKnownIndianState,
  listCitiesForIndianState,
  listIndianStates,
} from '@/lib/intake/masterGeoClientCache'
