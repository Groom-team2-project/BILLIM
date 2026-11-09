import os, sys, json, subprocess, time
from pathlib import Path
ROOT=Path('/Users/sobean/IdeaProjects/BILLIM')
os.environ['RENTAL_LIST_PROJECT']='billim-rental-verify-20261107'
os.environ['RENTAL_LIST_LOCAL']='/tmp/billim-rental-verify-20261107'
os.environ['RENTAL_LIST_RAW']=str(ROOT/'docs/performance/rental/raw/verification-20261107')
sys.path.insert(0,str(ROOT/'docs/performance/tools/rental'))
import compare_list as c
c.prepare()
results=[]
try:
    for case in ['borrowed','owner-requested']:
        for vus,duration in [(1,'30s'),(10,'30s'),(30,'30s'),(10,'180s')]:
            stem=f'{case}-vu{vus}-{duration}'
            env=dict(os.environ,RENTAL_LIST_FIXTURE=str(c.FIXTURE),RENTAL_LIST_CASE=case,DURATION=duration,K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
            with (c.RAW/(stem+'.log')).open('w') as log:
                r=subprocess.run(['k6','run','--vus',str(vus),'--summary-export',str(c.RAW/(stem+'.json')),str(ROOT/'docs/performance/scripts/rental-list-compare.js')],env=env,stdout=log,stderr=subprocess.STDOUT)
            m=json.loads((c.RAW/(stem+'.json')).read_text())['metrics']
            row=dict(case=case,vus=vus,duration=duration,exitCode=r.returncode,requests=m['http_reqs']['count'],rps=m['http_reqs']['rate'],p95Ms=m['http_req_duration']['p(95)'],p99Ms=m['http_req_duration']['p(99)'],failed=m['http_req_failed']['value'],checks=m['checks']['value'])
            results.append(row)
            (c.RAW/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
            print(json.dumps(row),flush=True)
finally:
    c.sql("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE member_id IN (910000,910001) AND revoked_at IS NULL;")
    c.FIXTURE.unlink(missing_ok=True)
    print('임시 세션 폐기·인증 파일 삭제 완료',flush=True)
