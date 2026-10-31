"""실제 MySQL 목록 전후 3회 측정의 중위값·편차 집계."""
import json
from pathlib import Path
from statistics import median, pstdev

ROOT=Path(__file__).resolve().parents[2]
RAW=ROOT/'docs/performance/rental/raw/list-compare-20261031'
scrape_sql=sum(json.loads((RAW/'actuator-probe.json').read_text())['sqlDelta'].values())
rows=[]
for label in ('before','after'):
    for case in ('borrowed','owner-requested'):
        samples=[]
        diagnostic=json.loads((RAW/label/case/'diagnostic.json').read_text())
        for i in range(1,4):
            metrics=json.loads((RAW/label/case/f'run-{i}.json').read_text())['metrics']
            observation=json.loads((RAW/label/case/f'run-{i}-sql.json').read_text())
            assert metrics['checks']['value']==1 and metrics['http_req_failed']['value']==0
            assert observation['k6ExitCode']==0 and not observation['actuatorErrors']
            assert all(observation['sqlDelta'].get(key,0)==0 for key in ('insert','update','delete'))
            samples.append({'requests':metrics['http_reqs']['count'],'throughput':metrics['http_reqs']['rate'],
                'p50Ms':metrics['http_req_duration']['med'],'p95Ms':metrics['http_req_duration']['p(95)'],
                'p99Ms':metrics['http_req_duration']['p(99)'],'sqlPerRequest':observation['sqlPerRequest'],
                'sqlPerRequestExcludingScrape':(sum(observation['sqlDelta'].values())-scrape_sql*len(observation['actuatorSamples']))/observation['requests'],
                'actuatorSamples':len(observation['actuatorSamples'])})
        row={'variant':label,'case':case,'singleRequestSql':sum(diagnostic['accountSqlDelta'].values())}
        for metric in samples[0]:
            values=[s[metric] for s in samples]
            row[metric]={'median':round(median(values),3),'min':round(min(values),3),'max':round(max(values),3),'stddev':round(pstdev(values),3)}
        rows.append(row)
(RAW/'summary.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
for row in rows:
    print(row['variant'],row['case'],'단일 요청 SQL',row['singleRequestSql'],'구간 SQL/요청',row['sqlPerRequest'],'p95',row['p95Ms'])
