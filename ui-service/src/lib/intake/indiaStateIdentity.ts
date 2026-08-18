/** Canonical India state identity — names, geo-master codes, and ISO 3166-2:IN short codes. */

const SHORT_CODE_TO_TOKEN: Record<string, string> = {
  AN: 'ANDAMANANDNICOBARISLANDS',
  AP: 'ANDHRAPRADESH',
  AR: 'ARUNACHALPRADESH',
  AS: 'ASSAM',
  BR: 'BIHAR',
  CH: 'CHANDIGARH',
  CT: 'CHHATTISGARH',
  CG: 'CHHATTISGARH',
  DN: 'DADRAANDNAGARHAVELI',
  DD: 'DAMANANDDIU',
  DL: 'DELHI',
  GA: 'GOA',
  GJ: 'GUJARAT',
  HR: 'HARYANA',
  HP: 'HIMACHALPRADESH',
  JK: 'JAMMUANDKASHMIR',
  JH: 'JHARKHAND',
  KA: 'KARNATAKA',
  KL: 'KERALA',
  LA: 'LADAKH',
  LD: 'LAKSHADWEEP',
  MP: 'MADHYAPRADESH',
  MH: 'MAHARASHTRA',
  MN: 'MANIPUR',
  ML: 'MEGHALAYA',
  MZ: 'MIZORAM',
  NL: 'NAGALAND',
  OR: 'ODISHA',
  OD: 'ODISHA',
  PY: 'PUDUCHERRY',
  PB: 'PUNJAB',
  RJ: 'RAJASTHAN',
  SK: 'SIKKIM',
  TN: 'TAMILNADU',
  TG: 'TELANGANA',
  TS: 'TELANGANA',
  TR: 'TRIPURA',
  UP: 'UTTARPRADESH',
  UT: 'UTTARAKHAND',
  UK: 'UTTARAKHAND',
  WB: 'WESTBENGAL',
}

export function indiaStateIdentity(raw: string | null | undefined): string {
  const token = String(raw ?? '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
  if (!token) return ''
  return SHORT_CODE_TO_TOKEN[token] ?? token
}

export function indiaStateMatchesAllowed(candidate: string, allowed: string[] | null | undefined): boolean {
  if (!allowed || allowed.length === 0) return true
  const identity = indiaStateIdentity(candidate)
  if (!identity) return true
  return allowed.some((a) => indiaStateIdentity(a) === identity)
}
