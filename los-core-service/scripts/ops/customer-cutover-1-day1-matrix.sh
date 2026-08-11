#!/bin/bash
# Day-1 exhaustive matrix after intake-scoped conflict fix.
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
OUT=/tmp/cutover_day1
mkdir -p "$OUT"

python3 - <<'PY'
import json, urllib.request, subprocess, os

BASE=os.environ.get('BASE','http://127.0.0.1:8083')
HDR={'X-User-Role':'CREDIT_MANAGER','X-User-Id':'a1000000-0000-0000-0000-000000000002','Content-Type':'application/json'}

def psql(sql):
    return subprocess.check_output(['docker','exec','billiontech-postgres','psql','-U','los_app','-d','los_core_staging','-Atc',sql], text=True)

def compose(body):
    req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/product-configuration/compose',
        data=json.dumps(body).encode(), headers=HDR, method='POST')
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode())

wfs={}
for line in psql("""SELECT borrower_type||'|'||loan_product||'|'||COALESCE(intake_segment,'BORROWER')||'|'||id::text||'|'||name||'|'||version::text||'|'||COALESCE(lms_product_code,'')
FROM workflow_configs WHERE active=true
AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
AND borrower_type IN ('INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY') ORDER BY 1;""").splitlines():
    bt,lp,seg,wid,name,ver,lms=line.split('|',6)
    wfs.setdefault((bt,lp,seg), []).append(dict(id=wid,name=name,version=int(ver),lms=lms))

rules={}
for line in psql("""SELECT borrower_type||'|'||loan_product||'|'||id::text||'|'||name||'|'||COALESCE(priority::text,'0')||'|'||COALESCE(min_amount::text,'')||'|'||COALESCE(max_amount::text,'')
FROM underwriting_rule_sets WHERE active=true
AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
AND borrower_type IN ('INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY')
ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;""").splitlines():
    bt,lp,rid,name,pri,mn,mx=line.split('|',6)
    rules.setdefault((bt,lp), []).append(dict(id=rid,name=name,priority=int(pri),min=mn,max=mx))

scs={}
for line in psql("""SELECT borrower_type||'|'||loan_product||'|'||id::text||'|'||name||'|'||COALESCE(priority::text,'0')||'|'||COALESCE(version::text,'1')||'|'||COALESCE(min_amount::text,'')||'|'||COALESCE(max_amount::text,'')
FROM underwriting_scorecards WHERE status='ACTIVE'
AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
AND borrower_type IN ('INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY')
ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;""").splitlines():
    bt,lp,sid,name,pri,ver,mn,mx=line.split('|',7)
    scs.setdefault((bt,lp), []).append(dict(id=sid,name=name,priority=int(pri),version=int(ver),min=mn,max=mx))

def fnum(s):
    if s is None or s=='': return None
    try: return float(s)
    except: return None

def pick_sc(cands, amt):
    for c in cands:
        mn,mx=fnum(c['min']),fnum(c['max'])
        if mn is not None and amt < mn: continue
        if mx is not None and amt > mx: continue
        return c
    return None

def certified_range(rchoices, schoices):
    # intersection of scorecard bands that can be selected + rule amount filters (union of matching rules)
    if not schoices: return None, None
    # For certification: union of ACTIVE scorecard bands (runtime can select any via priority+amount)
    mins=[fnum(c['min']) for c in schoices if fnum(c['min']) is not None]
    maxs=[fnum(c['max']) for c in schoices if fnum(c['max']) is not None]
    if not mins or not maxs: return None, None
    lo, hi = min(mins), max(maxs)
    # tighten by rule sets that have both bounds (all matching rules must allow — use intersection of rules with bounds)
    for r in rchoices:
        mn,mx=fnum(r['min']),fnum(r['max'])
        if mn is not None: lo=max(lo, mn)
        if mx is not None: hi=min(hi, mx)
    if lo>hi: return None, None
    return lo, hi

rows=[]
borrowers=['INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY']
products=[('TERM_LOAN','Term Loan'),('BUSINESS_TERM_LOAN','Business Loan'),('BUSINESS_WC_INVOICE_DISCOUNTING','Invoice Discounting')]
for bt in borrowers:
  for lp, bname in products:
    segs=['BORROWER'] + (['ANCHOR'] if lp=='BUSINESS_WC_INVOICE_DISCOUNTING' else [])
    for seg in segs:
      wch=wfs.get((bt,lp,seg), [])
      rch=rules.get((bt,lp), [])
      sch=scs.get((bt,lp), [])
      lo,hi=certified_range(rch, sch)
      mid = 250000.0
      if lo is not None and hi is not None:
        mid=min(max(250000.0, lo), hi)
      blockers=[]
      status='NOT READY'
      d=None
      if not wch:
        blockers.append('NO_WORKFLOW')
        wf_match='NO MATCH'
      elif len(wch)>1:
        blockers.append('AMBIGUOUS_WORKFLOWS')
        wf_match='AMBIGUOUS'
      else:
        wf_match='MATCH'
        wf=wch[0]
        if not rch: blockers.append('NO_LIVE_RULE_SET')
        if not sch: blockers.append('NO_SCORECARD')
        sc=pick_sc(sch, mid) if sch else None
        if sch and not sc: blockers.append(f'NO_SCORECARD_AT_MIDPOINT_{mid}')
        rs=rch[0] if rch else None
        if wf and rs and sc:
          d=compose({'borrowerType':bt,'loanProduct':lp,'intakeSegment':seg,
                     'workflowId':wf['id'],'liveRuleSetId':rs['id'],'scorecardId':sc['id'],'amount':mid})
          ready=bool(d.get('ready') or (d.get('readiness') or {}).get('ready'))
          amb=((d.get('conflicts') or {}).get('ambiguous'))
          match=d.get('productConfigMatchesRuntime')
          if amb: blockers.append('COMPOSE_AMBIGUOUS:'+str((d.get('conflicts') or {}).get('blockingMessages')))
          if match is False: blockers.append('PC_RUNTIME_MISMATCH')
          lms=((d.get('compose') or {}).get('lms') or {})
          if not lms.get('lmsProductCode'): blockers.append('LMS_MISSING')
          # amount boundary notes
          if lo is not None:
            blockers.append(f'CERTIFIED_RANGE={int(lo)}..{int(hi)}')
            blockers.append('OUT_OF_RANGE_BELOW_OR_ABOVE=NOT_CERTIFIED')
          info=(d.get('conflicts') or {}).get('informationalMessages') or []
          # READY for Day-1 certified range if compose ready and no structural blockers
          structural=[b for b in blockers if not b.startswith('CERTIFIED_RANGE') and not b.startswith('OUT_OF_RANGE')]
          if ready and not structural:
            status='READY'
          elif ready and structural:
            status='NOT READY'
          else:
            status='NOT READY' if structural else ('READY' if ready else 'NOT READY')
          # clean READY: only CERTIFIED notes allowed
          structural2=[b for b in blockers if b not in (f'CERTIFIED_RANGE={int(lo)}..{int(hi)}' if lo else '', 'OUT_OF_RANGE_BELOW_OR_ABOVE=NOT_CERTIFIED') and not b.startswith('CERTIFIED')]
          if ready and not any(x.startswith('NO_') or x.startswith('AMBIGUOUS') or x.startswith('COMPOSE') or x.startswith('PC_') or x.startswith('LMS') for x in blockers):
            status='READY'
          params=(d.get('readiness') or {}).get('requiredParameters') or []
          rows.append(dict(
            bt=bt, product=bname, lp=lp, seg=seg, status=status, wf_match=wf_match,
            wf=wf['name'], wf_ver=wf['version'], wf_id=wf['id'],
            rs=rs['name'], rs_id=rs['id'], rs_pri=rs['priority'], rs_count=len(rch),
            sc=sc['name'], sc_id=sc['id'], sc_pri=sc['priority'], sc_ver=sc['version'],
            sc_min=sc['min'], sc_max=sc['max'],
            certified_lo=lo, certified_hi=hi, mid=mid,
            lms=lms.get('lmsProductCode'), lms_src=lms.get('mappingSource'),
            plp=((d.get('compose') or {}).get('plp') or {}).get('status'),
            match=match, ready=ready, info=info,
            params=[{'id':p.get('parameterId'),'source':p.get('source'),'type':p.get('type')} for p in params],
            blockers=[b for b in blockers if not b.startswith('CERTIFIED') and b!='OUT_OF_RANGE_BELOW_OR_ABOVE=NOT_CERTIFIED'],
            certified_range=f"{int(lo)}-{int(hi)}" if lo is not None else None,
          ))
          open(f'/tmp/cutover_day1/{bt}_{lp}_{seg}.json','w').write(json.dumps(d, indent=2))
          continue
      rows.append(dict(bt=bt, product=bname, lp=lp, seg=seg, status=status, wf_match=wf_match if wch else 'NO MATCH',
                        blockers=blockers, certified_lo=lo, certified_hi=hi,
                        wf=wch[0]['name'] if len(wch)==1 else None,
                        rs=rch[0]['name'] if rch else None, sc=None, lms=wch[0]['lms'] if len(wch)==1 else None,
                        plp='NOT_REQUIRED', params=[], info=[], match=None, ready=False,
                        certified_range=f"{int(lo)}-{int(hi)}" if lo is not None else None))

print('=== DAY-1 MATRIX ===')
print(f"{'BT':12} {'Product':20} {'Seg':8} {'Status':10} {'CertRange':22} {'WF':34} {'Rules':28} {'Scorecard':30} {'LMS':11} Blockers")
ready_list=[]; not_ready=[]
for r in rows:
  cr=r.get('certified_range') or '-'
  bl=';'.join(r.get('blockers') or [])[:60]
  print(f"{r['bt']:12} {r['product']:20} {r['seg']:8} {r['status']:10} {cr:22} {(r.get('wf') or '-'):34.34} {(r.get('rs') or '-'):28.28} {(r.get('sc') or '-'):30.30} {str(r.get('lms') or '-'):11.11} {bl}")
  key=f"{r['bt']} × {r['lp']} × {r['seg']}" + (f" @ ₹{r['certified_range']}" if r.get('certified_range') else '')
  if r['status']=='READY':
    ready_list.append(key)
  else:
    not_ready.append(key+': '+';'.join(r.get('blockers') or ['unknown']))

print('\nREADY FOR DAY-1:')
for x in ready_list: print(' ', x)
print('\nNOT READY:')
for x in not_ready: print(' ', x)

# go-live
body=json.dumps({'customerName':'Billionloans Financial Services Private Limited','customerCode':'BILLIONLOANS',
  'targetGoLive':'2026-09-01','borrowerType':'COMPANY','loanProduct':'TERM_LOAN',
  'customerConfigSupplied':True,'includeDay1Matrix':True,'amount':250000}).encode()
req=urllib.request.Request(BASE+'/api/v1/admin/live-readiness/customer-go-live', data=body, headers=HDR, method='POST')
with urllib.request.urlopen(req, timeout=60) as resp:
  gl=json.loads(resp.read().decode())
print('\n=== GO-LIVE ===')
print('safe', gl.get('safeToGoLiveAnswer'), 'layers', gl.get('readinessLayers'))
for c in gl.get('checks') or []:
  if not c.get('pass'): print('FAIL', c.get('id'), c.get('detail'))

open('/tmp/cutover_day1/matrix.json','w').write(json.dumps({'ready':ready_list,'notReady':not_ready,'rows':rows}, indent=2))
print('Wrote /tmp/cutover_day1/matrix.json')
PY
