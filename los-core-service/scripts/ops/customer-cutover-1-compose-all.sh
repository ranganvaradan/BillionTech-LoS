#!/bin/bash
# Compose every launch Borrower×Product×Intake and summarise READY/blockers.
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
OUT=/tmp/cutover_compose
mkdir -p "$OUT"

echo '=== ACTIVE WORKFLOWS (launch products) ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, loan_product, intake_segment, name, version, active, id::text, COALESCE(lms_product_code,'(null)') AS lms
FROM workflow_configs
WHERE active = true
  AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
ORDER BY borrower_type, loan_product, intake_segment, version DESC;
"

echo '=== ACTIVE LIVE RULE SETS (launch products) ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, loan_product, name, active, priority, id::text
FROM underwriting_rule_sets
WHERE active = true
  AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;
"

echo '=== ACTIVE SCORECARDS COUNT PER SCOPE (launch) ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, loan_product, count(*) AS active_scorecards,
       string_agg(name || ' (p' || priority || ' ' || min_amount || '-' || max_amount || ')', ' | ' ORDER BY priority DESC) AS cards
FROM underwriting_scorecards
WHERE status='ACTIVE'
  AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
GROUP BY borrower_type, loan_product
ORDER BY 1,2;
"

python3 - <<'PY'
import json, urllib.request, os

BASE='http://127.0.0.1:8083'
HDR={'X-User-Role':'CREDIT_MANAGER','X-User-Id':'a1000000-0000-0000-0000-000000000002','Content-Type':'application/json'}

borrowers=['INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY']
products=[
  ('Term Loan','TERM_LOAN'),
  ('Business Loan','BUSINESS_TERM_LOAN'),
  ('Invoice Discounting','BUSINESS_WC_INVOICE_DISCOUNTING'),
]
extra_anchor={'BUSINESS_WC_INVOICE_DISCOUNTING'}

rows=[]
for bt in borrowers:
  for bname, lp in products:
    segs=['BORROWER'] + (['ANCHOR'] if lp in extra_anchor else [])
    for seg in segs:
      body=json.dumps({
        'borrowerType': bt,
        'loanProduct': lp,
        'intakeSegment': seg,
        'amount': 250000
      }).encode()
      req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/product-configuration/compose', data=body, headers=HDR, method='POST')
      try:
        with urllib.request.urlopen(req, timeout=90) as r:
          d=json.loads(r.read().decode())
      except Exception as e:
        rows.append({'bt':bt,'product':bname,'lp':lp,'seg':seg,'error':str(e),'ready':False})
        continue
      compose=d.get('compose') or {}
      readiness=d.get('readiness') or {}
      wf=compose.get('workflow') or {}
      rs=compose.get('liveRuleSet') or compose.get('ruleSet') or {}
      sc=compose.get('scorecard') or {}
      lms=compose.get('lms') or {}
      plp=compose.get('plp') or {}
      conflicts=d.get('conflicts') or {}
      blockers=d.get('goLiveBlockers') or readiness.get('gaps') or []
      req_params=readiness.get('requiredParameters') or []
      sources=sorted({(p.get('source') or '?') for p in req_params if isinstance(p, dict)})
      open_res=(lms.get('openLoanAccountResolution') or {}) if isinstance(lms, dict) else {}
      rows.append({
        'bt': bt,
        'product': bname,
        'lp': lp,
        'seg': seg,
        'ready': bool(readiness.get('ready') or d.get('ready')),
        'status': readiness.get('status') or d.get('status'),
        'wf_name': wf.get('name'),
        'wf_ver': wf.get('version'),
        'wf_id': wf.get('id'),
        'rs_name': rs.get('name'),
        'rs_id': rs.get('id'),
        'rs_pri': rs.get('priority'),
        'sc_name': sc.get('name'),
        'sc_ver': sc.get('version'),
        'sc_id': sc.get('id'),
        'sc_status': sc.get('status'),
        'sc_pri': sc.get('priority'),
        'sc_min': sc.get('minAmount') or sc.get('min_amount'),
        'sc_max': sc.get('maxAmount') or sc.get('max_amount'),
        'lms': lms.get('lmsProductCode') or open_res.get('lmsProductCode'),
        'lms_status': lms.get('status') or open_res.get('status'),
        'plp': plp.get('status') if isinstance(plp, dict) else plp,
        'sources': sources,
        'param_count': len(req_params),
        'params': req_params,
        'blockers': blockers,
        'conflict_msgs': (conflicts.get('messages') if isinstance(conflicts, dict) else None),
        'refs': readiness.get('references') or {},
      })
      open(f'/tmp/cutover_compose/{bt}_{lp}_{seg}.json','w').write(json.dumps(d, indent=2))

print('=== COMPOSE MATRIX @ amount=250000 ===')
hdr=f"{'BT':12} {'Product':20} {'Seg':8} {'Rdy':4} {'Workflow':42} {'LiveRules':34} {'Scorecard':38} {'LMS':11} {'#P':3}"
print(hdr)
for r in rows:
  if r.get('error'):
    print(r['bt'], r['product'], r['seg'], 'ERR', r['error'][:100])
    continue
  bl=r['blockers']
  bls=''
  if isinstance(bl, list) and bl:
    bls=' | '+';'.join(str(x)[:50] for x in bl[:2])
  elif bl:
    bls=' | '+str(bl)[:80]
  if r.get('conflict_msgs'):
    bls += ' | conflicts:'+str(r['conflict_msgs'])[:80]
  print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {str(r['ready'])[:4]:4} {(r['wf_name'] or '-'):42.42} {(r['rs_name'] or '-'):34.34} {(r['sc_name'] or '-'):38.38} {str(r['lms'] or '-'):11.11} {r['param_count']:3}{bls}")

# detailed params for COMPANY TERM_LOAN
for key in ['COMPANY_TERM_LOAN_BORROWER','COMPANY_BUSINESS_TERM_LOAN_BORROWER','COMPANY_BUSINESS_WC_INVOICE_DISCOUNTING_BORROWER']:
  p=f'/tmp/cutover_compose/{key}.json'
  if not os.path.exists(p):
    continue
  d=json.load(open(p))
  r=d.get('readiness') or {}
  print(f'\n=== DETAIL {key} ===')
  print('ready', r.get('ready'), 'status', r.get('status'))
  print('refs', json.dumps(r.get('references'), indent=2)[:2500])
  print('requiredParameters:')
  for x in r.get('requiredParameters') or []:
    print(' ', json.dumps(x))
  print('gaps', r.get('gaps'))
  print('goLiveBlockers', d.get('goLiveBlockers'))
  print('conflicts', d.get('conflicts'))
  print('lms', (d.get('compose') or {}).get('lms'))
  print('plp', (d.get('compose') or {}).get('plp'))

body=json.dumps({'borrowerType':'COMPANY','loanProduct':'TERM_LOAN','customerConfigSupplied':True,'intakeSegment':'BORROWER'}).encode()
req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/customer-go-live', data=body, headers=HDR, method='POST')
with urllib.request.urlopen(req, timeout=60) as resp:
  gl=json.loads(resp.read().decode())
print('\n=== GO-LIVE COMPANY/TERM_LOAN customerConfigSupplied=true (staging profile) ===')
print('safe', gl.get('safeToGoLiveAnswer'), 'failed', gl.get('failedCheckCount'))
for c in gl.get('checks') or []:
  mark='OK' if c.get('pass') else 'FAIL'
  print(mark, c.get('id'), c.get('detail'))

open('/tmp/cutover_compose/matrix.json','w').write(json.dumps(rows, indent=2))
print('\nWrote /tmp/cutover_compose/matrix.json')
PY
