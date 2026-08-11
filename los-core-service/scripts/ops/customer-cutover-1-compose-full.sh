#!/bin/bash
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
OUT=/tmp/cutover_full
mkdir -p "$OUT"

python3 - <<'PY'
import json, urllib.request, subprocess, os

BASE='http://127.0.0.1:8083'
HDR={'X-User-Role':'CREDIT_MANAGER','X-User-Id':'a1000000-0000-0000-0000-000000000002','Content-Type':'application/json'}

def psql(sql):
    return subprocess.check_output(['docker','exec','billiontech-postgres','psql','-U','los_app','-d','los_core_staging','-Atc',sql], text=True)

def compose(body):
    data=json.dumps(body).encode()
    req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/product-configuration/compose', data=data, headers=HDR, method='POST')
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode())

# workflows
wfs={}
for line in psql("SELECT borrower_type||'|'||loan_product||'|'||intake_segment||'|'||id::text||'|'||name||'|'||version::text||'|'||COALESCE(lms_product_code,'') FROM workflow_configs WHERE active=true AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING') ORDER BY 1;").splitlines():
    bt,lp,seg,wid,name,ver,lms=line.split('|',6)
    wfs.setdefault((bt,lp,seg), []).append({'id':wid,'name':name,'version':int(ver),'lms':lms})

# rule sets by bt+lp (intake not a dimension)
rules={}
for line in psql("SELECT borrower_type||'|'||loan_product||'|'||id::text||'|'||name||'|'||COALESCE(priority::text,'0') FROM underwriting_rule_sets WHERE active=true AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING') ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;").splitlines():
    bt,lp,rid,name,pri=line.split('|',4)
    rules.setdefault((bt,lp), []).append({'id':rid,'name':name,'priority':int(pri)})

# scorecards
scs={}
for line in psql("SELECT borrower_type||'|'||loan_product||'|'||id::text||'|'||name||'|'||COALESCE(priority::text,'0')||'|'||COALESCE(version::text,'1')||'|'||status||'|'||COALESCE(min_amount::text,'')||'|'||COALESCE(max_amount::text,'') FROM underwriting_scorecards WHERE status='ACTIVE' AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING') ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;").splitlines():
    bt,lp,sid,name,pri,ver,status,mn,mx=line.split('|',8)
    scs.setdefault((bt,lp), []).append({'id':sid,'name':name,'priority':int(pri),'version':int(ver),'status':status,'min':mn,'max':mx})

borrowers=['INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY','LLP']
products=[('Term Loan','TERM_LOAN'),('Business Loan','BUSINESS_TERM_LOAN'),('Invoice Discounting','BUSINESS_WC_INVOICE_DISCOUNTING')]
rows=[]

for bt in borrowers:
  for bname, lp in products:
    segs=['BORROWER'] + (['ANCHOR'] if lp=='BUSINESS_WC_INVOICE_DISCOUNTING' else [])
    for seg in segs:
      base={'bt':bt,'product':bname,'lp':lp,'seg':seg}
      if bt=='LLP':
        rows.append({**base,'ready':False,'blockers':['LLP not in LOS BorrowerType enum — domain gap'],'wf_choices':[],'rs_choices':[],'sc_choices':[]})
        continue
      wchoices=wfs.get((bt,lp,seg), [])
      rchoices=rules.get((bt,lp), [])
      schoices=scs.get((bt,lp), [])
      blockers=[]
      if not wchoices:
        blockers.append('No active workflow')
      elif len(wchoices)>1:
        blockers.append('Multiple active workflows — business choice required: ' + ' OR '.join(w['name'] for w in wchoices))
      if not rchoices:
        blockers.append('No active Live Rule Set')
      elif len(rchoices)>1:
        # runtime evaluates ALL; note secondary sets
        top=rchoices[0]
        others=[r['name']+f"(p{r['priority']})" for r in rchoices[1:]]
        blockers.append(f'Multiple active Live Rule Sets — runtime evaluates ALL; top={top["name"]} (p{top["priority"]}); also active: {", ".join(others)}. Confirm deactivate secondary or accept multi-set evaluation.')
      if not schoices:
        blockers.append('No ACTIVE scorecard')
      # pick unique workflow / top rule / amount-aware scorecard at 250000
      if len(wchoices)!=1 or not rchoices or not schoices:
        rows.append({**base,'ready':False,'blockers':blockers,'wf_choices':wchoices,'rs_choices':rchoices,'sc_choices':schoices,
                     'wf_name': wchoices[0]['name'] if len(wchoices)==1 else None,
                     'rs_name': rchoices[0]['name'] if rchoices else None,
                     'sc_name': schoices[0]['name'] if schoices else None,
                     'lms': wchoices[0]['lms'] if len(wchoices)==1 else None})
        continue
      wf=wchoices[0]
      rs=rchoices[0]  # top priority for Product Config alignment
      # scorecard at 250000: first by priority where min<=amt<=max
      amt=250000
      sc=None
      for cand in schoices:
        try:
          mn=float(cand['min']) if cand['min']!='' else None
          mx=float(cand['max']) if cand['max']!='' else None
        except Exception:
          mn=mx=None
        if mn is not None and amt < mn: continue
        if mx is not None and amt > mx: continue
        sc=cand; break
      if sc is None:
        blockers.append(f'No scorecard amount-scope match at ₹{amt}')
        rows.append({**base,'ready':False,'blockers':blockers,'wf_choices':wchoices,'rs_choices':rchoices,'sc_choices':schoices,
                     'wf_name':wf['name'],'rs_name':rs['name'],'lms':wf['lms']})
        continue
      body={
        'borrowerType':bt,'loanProduct':lp,'intakeSegment':seg,
        'workflowId':wf['id'],'liveRuleSetId':rs['id'],'scorecardId':sc['id'],
        'amount': amt
      }
      d=compose(body)
      readiness=d.get('readiness') or {}
      compose_m=d.get('compose') or {}
      lms=compose_m.get('lms') or {}
      plp=compose_m.get('plp') or {}
      open_res=lms.get('openLoanAccountResolution') or {}
      params=readiness.get('requiredParameters') or []
      sources=sorted({(p.get('source') or '?') for p in params if isinstance(p,dict)})
      gaps=list(readiness.get('gaps') or []) + list(d.get('goLiveBlockers') or [])
      # amount coverage checks
      for label, a in [('30k',30000),('50cr',500000000)]:
        matched=None
        for cand in schoices:
          try:
            mn=float(cand['min']) if cand['min']!='' else None
            mx=float(cand['max']) if cand['max']!='' else None
          except Exception:
            continue
          if mn is not None and a < mn: continue
          if mx is not None and a > mx: continue
          matched=cand['name']; break
        if not matched:
          blockers.append(f'No ACTIVE scorecard covers ₹{label} ({a})')
      try:
        if sc['min'] and float(sc['min'])>30000:
          blockers.append(f'Selected scorecard min {sc["min"]} > business floor 30000')
        if sc['max'] and float(sc['max'])<500000000:
          blockers.append(f'Selected scorecard max {sc["max"]} < business ceiling 50cr')
      except Exception:
        pass
      # multi rule-set already in blockers
      if gaps:
        for g in gaps:
          gs=str(g)
          if gs not in blockers and 'Multiple active Scorecards' not in gs:
            # scorecard multiplicity resolved by priority+amount is OK if productConfigMatchesRuntime
            blockers.append(gs)
      conflicts=d.get('conflicts') or {}
      msgs=conflicts.get('messages') or []
      for m in msgs:
        if 'Multiple active Scorecards' in str(m):
          blockers.append(str(m)+' — runtime uses priority+amount (acceptable if intentional)')
        elif 'Multiple active Live Rule Sets' in str(m):
          pass  # already captured
        elif str(m) not in blockers:
          blockers.append(str(m))
      lms_code=lms.get('lmsProductCode') or open_res.get('lmsProductCode') or wf['lms']
      if not lms_code:
        blockers.append('LMS mapping missing')
      ready=bool(readiness.get('ready')) and not any(
        x.startswith('No ') or 'domain gap' in x or 'business choice' in x or 'Multiple active Live Rule Sets' in x or 'Multiple active workflows' in x
        for x in blockers
      )
      # amount gaps keep NOT READY for full business range but combination may be READY for mid-range launch
      amount_blockers=[b for b in blockers if '30k' in b or '50cr' in b or 'business floor' in b or 'business ceiling' in b]
      structural=[b for b in blockers if b not in amount_blockers and 'Multiple active Scorecards' not in b]
      multi_rs=any('Multiple active Live Rule Sets' in b for b in blockers)
      ready_mid=bool(readiness.get('ready')) and not structural and not multi_rs and bool(lms_code)
      row={
        **base,
        'ready_product_config': bool(readiness.get('ready')),
        'ready_mid_range': ready_mid,
        'ready_full_amount_range': ready_mid and not amount_blockers,
        'wf_name':wf['name'],'wf_ver':wf['version'],'wf_id':wf['id'],
        'rs_name':rs['name'],'rs_id':rs['id'],'rs_pri':rs['priority'],
        'rs_all':[{'name':r['name'],'priority':r['priority'],'id':r['id']} for r in rchoices],
        'sc_name':sc['name'],'sc_id':sc['id'],'sc_ver':sc['version'],'sc_pri':sc['priority'],
        'sc_min':sc['min'],'sc_max':sc['max'],
        'sc_all':schoices,
        'lms':lms_code,'lms_status':lms.get('status') or open_res.get('status'),
        'plp': plp.get('status') if isinstance(plp,dict) else plp,
        'sources':sources,'param_count':len(params),'params':params,
        'blockers':blockers,'amount_blockers':amount_blockers,'structural_blockers':structural,
        'match': conflicts.get('productConfigMatchesRuntime'),
        'refs': readiness.get('references'),
      }
      rows.append(row)
      open(f'/tmp/cutover_full/{bt}_{lp}_{seg}.json','w').write(json.dumps({'compose_request':body,'response':d,'summary':row}, indent=2))

print('=== CUTOVER RESOLUTION MATRIX ===')
print(f"{'BT':12} {'Product':20} {'Seg':8} {'Mid':5} {'Full₹':5} {'Workflow':36} {'Rules':30} {'Scorecard':34} {'LMS':11} Sources / Blockers")
for r in rows:
  if not r.get('wf_name') and r.get('blockers'):
    print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {'NO':5} {'NO':5} - - - - {';'.join(r['blockers'])[:100]}")
    continue
  src=','.join(r.get('sources') or []) or '-'
  bl=';'.join((r.get('structural_blockers') or [])+(r.get('amount_blockers') or [])[:2])[:70]
  print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {str(r.get('ready_mid_range')):5} {str(r.get('ready_full_amount_range')):5} {(r.get('wf_name') or '-'):36.36} {(r.get('rs_name') or '-'):30.30} {(r.get('sc_name') or '-'):34.34} {str(r.get('lms') or '-'):11.11} {src} || {bl}")

# COMPANY TERM_LOAN detail
for r in rows:
  if r.get('bt')=='COMPANY' and r.get('lp')=='TERM_LOAN' and r.get('seg')=='BORROWER':
    print('\n=== GOLDEN REFERENCE (COMPANY / TERM_LOAN / BORROWER @ ₹2.5L) ===')
    print(json.dumps({k:r[k] for k in r if k!='params'}, indent=2)[:5000])
    print('PARAMS:')
    for p in r.get('params') or []:
      print(' ', json.dumps(p))

# golden API
req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/product-configuration/golden', headers=HDR)
with urllib.request.urlopen(req, timeout=60) as resp:
  g=json.loads(resp.read().decode())
print('\n=== GOLDEN COMPOSE API ready ===', (g.get('readiness') or {}).get('ready'), (g.get('readiness') or {}).get('status'))
print('golden refs', json.dumps((g.get('readiness') or {}).get('references'), indent=2)[:2000])

open('/tmp/cutover_full/matrix.json','w').write(json.dumps(rows, indent=2))
print('Wrote /tmp/cutover_full/matrix.json')
PY
