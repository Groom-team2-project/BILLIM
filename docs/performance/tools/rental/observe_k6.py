"""k6 실행 구간의 SQL 계정 카운터·Actuator 원본 수집. 인증값 출력 금지."""

import json
import os
from pathlib import Path
import subprocess
import sys
import threading
import time

from run import COMPOSE, sql


def snapshot():
    text = sql("SELECT EVENT_NAME,SUM(COUNT_STAR) FROM performance_schema.events_statements_summary_by_account_by_event_name WHERE USER='rental_perf' AND EVENT_NAME IN ('statement/sql/select','statement/sql/insert','statement/sql/update','statement/sql/delete') GROUP BY EVENT_NAME;")
    return {row.split('\t')[0].rsplit('/',1)[1]:int(row.split('\t')[1]) for row in text.splitlines()}


def scrape():
    shell = "exec 3<>/dev/tcp/127.0.0.1/9090; printf 'GET /actuator/prometheus HTTP/1.0\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3; cat <&3"
    result = subprocess.run(COMPOSE+['exec','-T','backend','bash','-c',shell],capture_output=True,text=True,timeout=12)
    if result.returncode or '200' not in result.stdout.splitlines()[0]:
        raise RuntimeError('Actuator 수집 실패')
    return result.stdout


def main():
    args=sys.argv[1:]
    target=Path(args[args.index('--summary-export')+1])
    archive=Path(os.environ['RENTAL_ARCHIVE'])/os.environ['RENTAL_LABEL']
    archive.mkdir(parents=True,exist_ok=True)
    stem=target.stem
    before=snapshot()
    readings=[]
    errors=[]
    done=threading.Event()
    def sample():
        while not done.is_set():
            try:
                readings.append({'time':time.time(),'text':scrape()})
            except Exception as exc:
                errors.append(type(exc).__name__)
            done.wait(3)
    thread=threading.Thread(target=sample,daemon=True)
    thread.start()
    started=time.time()
    result=subprocess.run([os.environ['RENTAL_REAL_K6']]+args)
    elapsed=time.time()-started
    done.set()
    thread.join(timeout=15)
    after=snapshot()
    delta={key:after.get(key,0)-before.get(key,0) for key in set(before)|set(after)}
    metrics=json.loads(target.read_text()) if target.exists() else {}
    requests=metrics.get('metrics',{}).get('http_reqs',{}).get('count',0)
    prefixes=('process_cpu_usage','system_cpu_usage','jvm_memory_used_bytes','hikaricp_connections_active','tomcat_threads_busy_threads')
    server_samples=[{'time':reading['time'],'metrics':[line for line in reading['text'].splitlines() if line.startswith(prefixes)]} for reading in readings]
    observation={'elapsedSeconds':elapsed,'sqlBefore':before,'sqlAfter':after,'sqlDelta':delta,
        'sqlTotal':sum(delta.values()),'requests':requests,
        'sqlPerRequest':sum(delta.values())/requests if requests else None,
        'actuatorSamples':len(readings),'actuatorErrors':errors,'serverSamples':server_samples,'k6ExitCode':result.returncode}
    (archive/(stem+'-observation.json')).write_text(json.dumps(observation,indent=2)+'\n')
    if readings:
        (archive/f'{stem}-prometheus-first.txt').write_text(readings[0]['text'])
        (archive/f'{stem}-prometheus-last.txt').write_text(readings[-1]['text'])
    if metrics:
        (archive/(stem+'.json')).write_text(json.dumps(metrics,indent=2)+'\n')
    raise SystemExit(result.returncode)


if __name__=='__main__':
    main()
