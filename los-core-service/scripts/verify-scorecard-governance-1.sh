#!/bin/bash
set -eu
# Golden walkthrough — COMPANY TERM_LOAN governance (maker ≠ checker via headers)

login() {
  local email="$1" pass="$2"
  curl -sS -m 30 -X POST 'http://127.0.0.1:8083/api/v1/auth/login' \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}" -o /tmp/login.json
  python3 - <<'PY'
import json
d=json.load(open("/tmp/login.json"))
token=d.get("accessToken") or d.get("token") or (d.get("data") or {}).get("accessToken")
u=d.get("user") or d.get("data") or d
open("/tmp/staff.token","w").write(token or "")
uid=str(u.get("id") or u.get("userId") or "")
role=str(u.get("role") or "")
name=str(u.get("name") or u.get("email") or uid)
open("/tmp/staff.meta","w").write(json.dumps({"userId":uid,"role":role,"name":name}))
print("LOGIN", name, role, "token", bool(token))
PY
}

hdrs() {
  python3 - <<'PY'
import json
m=json.load(open("/tmp/staff.meta"))
print(f'-H "Authorization: Bearer {open("/tmp/staff.token").read().strip()}" -H "X-User-Id: {m["userId"]}" -H "X-User-Role: {m["role"]}" -H "X-User-Name: {m["name"]}"')
PY
}

login 'admin@credinnov.com' 'Bltest@123'
H=$(eval echo "$(hdrs)")

# Find company ACTIVE
python3 - <<'PY'
import json,urllib.request,os
token=open("/tmp/staff.token").read().strip()
meta=json.load(open("/tmp/staff.meta"))
req=urllib.request.Request('http://127.0.0.1:8083/api/v1/underwriting/scorecards',
    headers={"Authorization":f"Bearer {token}","X-User-Id":meta["userId"],"X-User-Role":meta["role"],"X-User-Name":meta["name"]})
cards=json.load(urllib.request.urlopen(req, timeout=60))
active=[c for c in cards if c.get("borrowerType")=="COMPANY" and c.get("loanProduct")=="TERM_LOAN" and c.get("active")]
print("ACTIVE_COMPANY", len(active))
if not active:
    raise SystemExit("no active company term loan")
c=active[0]
open("/tmp/sc_v1.json","w").write(json.dumps(c))
print("V1", c["id"], "v", c["version"], "bureau top", [r.get("score") for r in (c.get("scorecardJson") or {}).get("rows",[]) if r.get("parameter")=="BUREAU_SCORE" and r.get("condition")=="GTE:750"])
PY

# Create new version as maker (admin/CM)
V1=$(python3 -c 'import json;print(json.load(open("/tmp/sc_v1.json"))["id"])')
curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V1}/new-version" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" \
  -H "X-User-Id: $(python3 -c 'import json;print(json.load(open("/tmp/staff.meta"))["userId"])')" \
  -H "X-User-Role: CREDIT_MANAGER" \
  -H "X-User-Name: credit.manager.maker" \
  -o /tmp/sc_v2.json
python3 - <<'PY'
import json
d=json.load(open("/tmp/sc_v2.json"))
print("V2_DRAFT", d.get("id"), d.get("status"), d.get("version"))
assert d.get("status")=="DRAFT"
# bump bureau 35->40
rows=((d.get("scorecardJson") or {}).get("rows") or [])
out=[]
for r in rows:
    rr=dict(r)
    if rr.get("parameter")=="BUREAU_SCORE" and rr.get("condition")=="GTE:750":
        rr["score"]=40
    if rr.get("parameter")=="MONTHLY_INCOME":
        rr["legacyCustomJustified"]=True
        rr["mappingStatus"]="LEGACY_CUSTOM"
    out.append(rr)
d["scorecardJson"]=dict(d.get("scorecardJson") or {}, rows=out)
# factor policies
pols={}
for r in out:
    p=r.get("parameter")
    if p: pols[p]={"missingData":"REQUIRED"}
sj=dict(d.get("safetyJson") or {})
sj["factorPolicies"]=pols
sj["missingDataPoliciesExplicit"]=True
sj["missingDataPoliciesConfirmed"]=True
d["safetyJson"]=sj
d["active"]=False
open("/tmp/sc_v2_edit.json","w").write(json.dumps({
  "name": d["name"],
  "borrowerType": d["borrowerType"],
  "loanProduct": d["loanProduct"],
  "version": d["version"],
  "priority": d["priority"],
  "minAmount": d.get("minAmount"),
  "maxAmount": d.get("maxAmount"),
  "geography": d.get("geography"),
  "scorecardJson": d["scorecardJson"],
  "thresholdsJson": d["thresholdsJson"],
  "hardRulesJson": d.get("hardRulesJson") or {"rules":[]},
  "safetyJson": sj,
  "active": False
}))
open("/tmp/v2id","w").write(d["id"])
PY

V2=$(cat /tmp/v2id)
curl -sS -m 60 -X PUT "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: maker-gov-1" \
  -H "X-User-Role: CREDIT_MANAGER" \
  -H "X-User-Name: credit.manager.maker" \
  --data-binary @/tmp/sc_v2_edit.json -o /tmp/sc_v2_saved.json

curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/confirm-missing-data-policies" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" \
  -H "X-User-Id: maker-gov-1" -H "X-User-Role: CREDIT_MANAGER" -H "X-User-Name: credit.manager.maker" \
  -o /tmp/sc_v2_conf.json || true

curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/record-preview" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" -H "Content-Type: application/json" \
  -H "X-User-Id: maker-gov-1" -H "X-User-Role: CREDIT_MANAGER" -H "X-User-Name: credit.manager.maker" \
  -d '{"inputs":{"BUREAU_SCORE":760,"MONTHLY_INCOME":60000,"OBLIGATION_RATIO":30,"AVERAGE_BANK_BALANCE":25000,"KYC_QUALITY":1}}' \
  -o /tmp/sc_v2_prev.json

curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/submit-review" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" -H "Content-Type: application/json" \
  -H "X-User-Id: maker-gov-1" -H "X-User-Role: CREDIT_MANAGER" -H "X-User-Name: credit.manager.maker" \
  -d '{"remarks":"bureau points 40"}' -o /tmp/sc_v2_sub.json

# Self-approval must fail
curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/approve" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" -H "Content-Type: application/json" \
  -H "X-User-Id: maker-gov-1" -H "X-User-Role: POLICY_CHECKER" -H "X-User-Name: credit.manager.maker" \
  -d '{"remarks":"self"}' -o /tmp/sc_v2_self.json

# Checker approve
curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/approve" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" -H "Content-Type: application/json" \
  -H "X-User-Id: checker-gov-1" -H "X-User-Role: POLICY_CHECKER" -H "X-User-Name: policy.checker" \
  -d '{"remarks":"approved"}' -o /tmp/sc_v2_appr.json

# Activate
curl -sS -m 60 -X POST "http://127.0.0.1:8083/api/v1/underwriting/scorecards/${V2}/activate" \
  -H "Authorization: Bearer $(cat /tmp/staff.token)" \
  -H "X-User-Id: maker-gov-1" -H "X-User-Role: CREDIT_MANAGER" -H "X-User-Name: credit.manager.maker" \
  -o /tmp/sc_v2_act.json

python3 - <<'PY'
import json
def load(p):
    try: return json.load(open(p))
    except Exception as e: return {"_err":str(e),"_raw":open(p).read()[:400]}
sub=load("/tmp/sc_v2_sub.json"); self=load("/tmp/sc_v2_self.json"); ap=load("/tmp/sc_v2_appr.json"); act=load("/tmp/sc_v2_act.json")
print("SUBMIT_STATUS", sub.get("status"), "maker", (sub.get("governanceJson") or {}).get("submittedBy"))
print("SELF_BLOCK", self.get("reason") or self.get("message") or self.get("_raw"))
print("APPROVED_STATUS", ap.get("status"), "checker", (ap.get("governanceJson") or {}).get("approvedBy"))
print("ACTIVE_STATUS", act.get("status"), "active", act.get("active"))
# v1 retired?
import urllib.request
token=open("/tmp/staff.token").read().strip()
req=urllib.request.Request('http://127.0.0.1:8083/api/v1/underwriting/scorecards',
    headers={"Authorization":f"Bearer {token}","X-User-Id":"x","X-User-Role":"ADMINISTRATOR","X-User-Name":"x"})
cards=json.load(urllib.request.urlopen(req, timeout=60))
v1id=json.load(open("/tmp/sc_v1.json"))["id"]
v1=[c for c in cards if c["id"]==v1id][0]
v2=[c for c in cards if c["id"]==act.get("id")][0]
print("V1_AFTER", v1.get("status"), "active", v1.get("active"))
print("V2_AFTER", v2.get("status"), "active", v2.get("active"))
# bureau points
def top(c):
    for r in (c.get("scorecardJson") or {}).get("rows") or []:
        if r.get("parameter")=="BUREAU_SCORE" and r.get("condition")=="GTE:750":
            return r.get("score")
print("V1_BUREAU_TOP", top(v1), "V2_BUREAU_TOP", top(v2))
assert v1.get("status")=="RETIRED" and v1.get("active") is False
assert v2.get("status")=="ACTIVE" and v2.get("active") is True
assert top(v1)==35 and top(v2)==40
print("GOVERNANCE_GOLDEN_OK")
PY
