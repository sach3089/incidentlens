#!/usr/bin/env python3
"""Generate local synthetic traffic and an evidence report; Python stdlib only."""
import base64, datetime as dt, json, os, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
for line in Path('.env').read_text().splitlines():
    if line and not line.startswith('#') and '=' in line:
        k,v=line.split('=',1);os.environ.setdefault(k,v)
password=os.environ['INCIDENTLENS_PASSWORD'];key=os.environ['DEMO_KEY']
auth='Basic '+base64.b64encode(('analyst:'+password).encode()).decode()
def call(url,data=None,headers=None):
    req=urllib.request.Request(url,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json',**(headers or {})})
    try:
        with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
    except urllib.error.HTTPError as e:
        if url.endswith('/orders/demo'):return {'status':e.code}
        raise
def traffic(seconds):
    until=time.monotonic()+seconds
    def worker():
        while time.monotonic()<until:
            call('http://localhost:8081/api/v1/orders/demo');time.sleep(.15)
    with ThreadPoolExecutor(max_workers=3) as p:list(p.map(lambda _:worker(),range(3)))
def now():return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z')
call('http://localhost:8082/demo/fault',{'delayMs':0,'fail':False},{'X-Demo-Key':key})
print('Healthy baseline: 75 seconds');traffic(75)
start=now()
call('http://localhost:8080/api/v1/deployment-events',{'service':'order-service','version':'synthetic-demo-v2','occurredAt':start,'description':'Synthetic event only; not a real deployment'},{'Authorization':auth})
call('http://localhost:8082/demo/fault',{'delayMs':250,'fail':True},{'X-Demo-Key':key})
try:
    print('Controlled fault: 75 seconds');traffic(75)
    end=now();print('Allowing telemetry export: 15 seconds');time.sleep(15)
    report=call('http://localhost:8080/api/v1/investigations',{'service':'order-service','environment':'local','from':start,'to':end},{'Authorization':auth})
    Path('report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
finally:
    call('http://localhost:8082/demo/fault',{'delayMs':0,'fail':False},{'X-Demo-Key':key})
