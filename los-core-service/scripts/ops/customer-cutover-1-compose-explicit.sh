#!/bin/bash
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
OUT=/tmp/cutover_explicit
mkdir -p "$OUT"

python3 - <<'PY'
import json, urllib.request, subprocess

BASE='http://127.0.0.1:8083'
HDR={'X-User-Role':'CREDIT_MANAGER','X-User-Id':'a1000000-0000-0000-0000-000000000002','Content-Type':'application/json'}

# Pull active workflows for launch products
sql="""SELECT borrower_type||'|'||loan_product||'|'||intake_segment||'|'||id::text||'|'||name||'|'||version::text||'|'||COALESCE(lms_product_code,'')
FROM workflow_configs
WHERE active=true AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
ORDER BY 1;"""
raw=subprocess.check_output(['docker','exec','billiontech-postgres','psql','-U','los_app','-d','los_core_staging','-Atc',sql], text=True)
wfs={}
for line in raw.splitlines():
    if not line.strip(): continue
    bt,lp,seg,wid,name,ver,lms=line.split('|',6)
    wfs.setdefault((bt,lp,seg), []).append({'id':wid,'name':name,'version':int(ver),'lms':lms})

borrowers=['INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY','LLP']
products=[('Term Loan','TERM_LOAN'),('Business Loan','BUSINESS_TERM_LOAN'),('Invoice Discounting','BUSINESS_WC_INVOICE_DISCOUNTING')]
rows=[]

def compose(body):
    data=json.dumps(body).encode()
    req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/product-configuration/compose', data=data, headers=HDR, method='POST')
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode())

for bt in borrowers:
  for bname, lp in products:
    segs=['BORROWER'] + (['ANCHOR'] if lp=='BUSINESS_WC_INVOICE_DISCOUNTING' else [])
    for seg in segs:
      if bt=='LLP':
        rows.append({'bt':bt,'product':bname,'lp':lp,'seg':seg,'ready':False,'blocker':'LLP not in LOS BorrowerType enum','wf_choices':[]})
        continue
      choices=wfs.get((bt,lp,seg), [])
      if len(choices)==0:
        rows.append({'bt':bt,'product':bname,'lp':lp,'seg':seg,'ready':False,'blocker':'No active workflow for combination','wf_choices':[]})
        continue
      if len(choices)>1:
        rows.append({'bt':bt,'product':bname,'lp':lp,'seg':seg,'ready':False,'blocker':'Multiple active workflows — business choice required','wf_choices':choices})
        continue
      wf=choices[0]
      d=compose({'borrowerType':bt,'loanProduct':lp,'intakeSegment':seg,'workflowId':wf['id'],'amount':250000})
      compose_m=d.get('compose') or {}
      readiness=d.get('readiness') or {}
      rs=compose_m.get('liveRuleSet') or {}
      sc=compose_m.get('scorecard') or {}
      lms=compose_m.get('lms') or {}
      plp=compose_m.get('plp') or {}
      open_res=lms.get('openLoanAccountResolution') or {}
      params=readiness.get('requiredParameters') or []
      sources=sorted({(p.get('source') or '?') for p in params if isinstance(p,dict)})
      conflicts=d.get('conflicts') or {}
      gaps=readiness.get('gaps') or d.get('goLiveBlockers') or []
      # also amount extremes for scorecard coverage
      d_lo=compose({'borrowerType':bt,'loanProduct':lp,'intakeSegment':seg,'workflowId':wf['id'],'amount':30000})
      d_hi=compose({'borrowerType':bt,'loanProduct':lp,'intakeSegment':seg,'workflowId':wf['id'],'amount':500000000})
      sc_lo=((d_lo.get('compose') or {}).get('scorecard') or {}).get('name')
      sc_hi=((d_hi.get('compose') or {}).get('scorecard') or {}).get('name')
      ready=bool(readiness.get('ready'))
      row={
        'bt':bt,'product':bname,'lp':lp,'seg':seg,
        'ready':ready,'status':readiness.get('status'),
        'wf_name':wf['name'],'wf_ver':wf['version'],'wf_id':wf['id'],'wf_lms':wf['lms'],
        'rs_name':rs.get('name'),'rs_id':rs.get('id'),'rs_pri':rs.get('priority'),
        'sc_name':sc.get('name'),'sc_id':sc.get('id'),'sc_ver':sc.get('version'),'sc_status':sc.get('status'),
        'sc_pri':sc.get('priority'),
        'sc_min':sc.get('minAmount'),'sc_max':sc.get('maxAmount'),
        'sc_at_30k':sc_lo,'sc_at_50cr':sc_hi,
        'lms': lms.get('lmsProductCode') or open_res.get('lmsProductCode') or wf['lms'],
        'lms_status': lms.get('status') or open_res.get('status'),
        'plp': plp.get('status') if isinstance(plp,dict) else plp,
        'sources': sources,'param_count':len(params),'params':params,
        'gaps': gaps,
        'conflict_msgs': conflicts.get('messages') if isinstance(conflicts,dict) else None,
        'refs': readiness.get('references'),
        'match': (conflicts.get('productConfigMatchesRuntime') if isinstance(conflicts,dict) else None),
      }
      # blocker summary
      blockers=[]
      if not rs.get('id'): blockers.append('No Live Rule Set')
      if not sc.get('id'): blockers.append('No Scorecard')
      if not row['lms']: blockers.append('LMS mapping missing')
      if gaps: blockers.extend([str(g) for g in gaps[:5]])
      if row.get('conflict_msgs'): blockers.append(str(row['conflict_msgs'])[:120])
      # amount coverage vs business 30k-50cr using selected scorecard at 250k
      smin=sc.get('minAmount'); smax=sc.get('maxAmount')
      try:
        if smin is not None and float(smin)>30000: blockers.append(f'Scorecard min {smin} > business min 30000')
        if smax is not None and float(smax)<500000000: blockers.append(f'Scorecard max {smax} < business max 50cr')
      except Exception:
        pass
      if not sc_lo: blockers.append('No scorecard match at ₹30,000')
      if not sc_hi: blockers.append('No scorecard match at ₹50cr')
      row['blockers']=blockers
      row['ready_cutover']= ready and not blockers and bool(rs.get('id')) and bool(sc.get('id')) and bool(row['lms'])
      rows.append(row)
      open(f'/tmp/cutover_explicit/{bt}_{lp}_{seg}.json','w').write(json.dumps(d, indent=2))

print('=== EXPLICIT WORKFLOW COMPOSE MATRIX ===')
print(f"{'BT':12} {'Product':20} {'Seg':8} {'CutRdy':6} {'Workflow':40} {'Rules':32} {'Scorecard':36} {'LMS':11} Blockers")
for r in rows:
  if 'wf_choices' in r and r.get('blocker') and not r.get('wf_name'):
    ch=';'.join(c['name'] for c in r.get('wf_choices') or [])
    print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {'NO':6} blocker={r['blocker']} {ch}")
    continue
  bl=';'.join(r.get('blockers') or [])[:90]
  print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {str(r.get('ready_cutover')):6} {(r.get('wf_name') or '-'):40.40} {(r.get('rs_name') or '-'):32.32} {(r.get('sc_name') or '-'):36.36} {str(r.get('lms') or '-'):11.11} {bl}")

# Golden reference pick: prefer COMPANY TERM_LOAN BORROWER if cutover-ready else first ready_cutover
gold=None
for r in rows:
  if r.get('bt')=='COMPANY' and r.get('lp')=='TERM_LOAN' and r.get('seg')=='BORROWER':
    gold=r; break
print('\n=== GOLDEN REFERENCE CANDIDATE ===')
print(json.dumps({k:gold.get(k) for k in gold} if gold else None, indent=2)[:4000])

# Detail params for COMPANY TERM_LOAN
p='/tmp/cutover_explicit/COMPANY_TERM_LOAN_BORROWER.json'
if __import__('os').path.exists(p):
  d=json.load(open(p))
  print('\n=== COMPANY TERM_LOAN requiredParameters ===')
  for x in (d.get('readiness') or {}).get('requiredParameters') or []:
    print(json.dumps(x))
  print('refs', json.dumps((d.get('readiness') or {}).get('references'), indent=2)[:2000])
  print('lms', (d.get('compose') or {}).get('lms'))
  print('plp', (d.get('compose') or {}).get('plp'))

open('/tmp/cutover_explicit/matrix.json','w').write(json.dumps(rows, indent=2))
print('\nWrote /tmp/cutover_explicit/matrix.json')
PY
