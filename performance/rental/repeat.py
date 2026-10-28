"""같은 초기 DB·고정 요청 키 순서로 5개 시나리오를 준비 후 3회 반복."""

from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time
import uuid

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
ARCHIVE=ROOT/'docs/performance/rental/raw/repeated'
COMPOSE=['docker','compose','-f',str(HERE/'compose.yaml')]


def worker():
    import run
    import run_approval
    import run_appointment
    import run_create
    counter=0
    original=uuid.uuid5
    def deterministic_uuid():
        nonlocal counter
        counter+=1
        return original(uuid.NAMESPACE_URL,f'billim-rental-repeat-{counter}')
    uuid.uuid4=deterministic_uuid
    tasks=[('list',run.main),('approval-warmup',run_approval.main),('approval',run_approval.main),
        ('idempotency-warmup',lambda:run_approval.main('idempotency')),
        ('idempotency',lambda:run_approval.main('idempotency')),
        ('appointment-warmup',run_appointment.main),('appointment',run_appointment.main),
        ('create',run_create.main)]
    if os.environ.get('RENTAL_CREATE_ONLY')=='1':
        os.environ['RENTAL_SEED_ONLY']='1'
        os.environ['RENTAL_FIXED_WARMUP']='1'
        os.environ['RENTAL_RESET_WARMUP']='1'
        tasks=[('seed',run.main),('create',run_create.main)]
    elif os.environ.get('RENTAL_CORE_ONLY')=='1':
        tasks=[(label,task) for label,task in tasks if label!='create']
    results={}
    for label,task in tasks:
        os.environ['RENTAL_LABEL']=label
        print(f'시나리오 {label} 시작',flush=True)
        try:
            task()
            results[label]=0
        except SystemExit as exc:
            results[label]=exc.code if isinstance(exc.code,int) else 1
            print(f'시나리오 {label}: 검증 실패도 원본 보존 후 계속',flush=True)
        destination=Path(os.environ['RENTAL_ARCHIVE'])/label
        destination.mkdir(parents=True,exist_ok=True)
        for f in run.LOCAL.glob('*verification.json'):
            shutil.copyfile(f,destination/f.name)
        stems={'list':['smoke','warmup','baseline'],'approval':['approval'],'idempotency':['idempotency'],
            'appointment':['appointment-race'],'create':['create-smoke','create-warmup','create-baseline','create-replay']}
        for stem in stems.get(label.replace('-warmup',''),[]):
            log=run.LOCAL/(stem+'.log')
            if log.exists():
                shutil.copyfile(log,destination/log.name)
        (Path(os.environ['RENTAL_ARCHIVE'])/'scenario-exits.json').write_text(json.dumps(results,indent=2)+'\n')


def main():
    ARCHIVE.mkdir(parents=True,exist_ok=True)
    real_k6=shutil.which('k6')
    if not real_k6:
        raise SystemExit('k6 설치 필요')
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    batch=ARCHIVE/stamp
    batch.mkdir()
    override=HERE/'local/repeat-override.json'
    override.parent.mkdir(mode=0o700,exist_ok=True)
    override.write_text(json.dumps({'services':{'backend':{'environment':{
        'BILLIM_RENTAL_EXPIRATION_DELAY_MS':'86400000','CLEANUP_CRON':'-'}}}}))
    manifest={'startedAt':datetime.now(timezone.utc).isoformat(),'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'repetitions':3,'sqlDefinition':'애플리케이션 DB 계정의 SELECT/INSERT/UPDATE/DELETE 실행 횟수 차이 / HTTP 요청 수',
        'beforeAfterComparison':False,'rounds':[]}
    manifest['createOnly']=os.environ.get('RENTAL_CREATE_ONLY')=='1'
    jar=ROOT/'backend/build/libs/billim-0.0.1-SNAPSHOT.jar'
    manifest['backendJarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
    (batch/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for repeat in range(1,4):
        project=f'billim-rental-repeat-{stamp.lower()}-{repeat}'
        command=COMPOSE+['-f',str(override),'-p',project]
        output=batch/f'run-{repeat}'
        output.mkdir()
        local=HERE/'local'/stamp/f'run-{repeat}'
        local.mkdir(parents=True,mode=0o700)
        bindir=local/'bin'
        bindir.mkdir()
        launcher=bindir/'k6'
        launcher.write_text('#!/bin/sh\nexec '+shlex.quote(sys.executable)+' '+shlex.quote(str(HERE/'observe_k6.py'))+' "$@"\n')
        launcher.chmod(0o700)
        env=dict(os.environ,RENTAL_PERF_PROJECT=project,RENTAL_PERF_LOCAL=str(local),
            RENTAL_FIXTURE_DIR=str(local),RENTAL_ARCHIVE=str(output),RENTAL_REAL_K6=real_k6,
            PATH=str(bindir)+os.pathsep+os.environ['PATH'])
        print(f'반복 {repeat}/3: 새 전용 DB 실행',flush=True)
        subprocess.run(command+['up','-d','--build','--wait'],check=True,env=env)
        try:
            with (output/'execution.log').open('w') as log:
                result=subprocess.run([sys.executable,str(Path(__file__).resolve()),'--worker'],env=env,stdout=log,stderr=subprocess.STDOUT)
            manifest['rounds'].append({'repeat':repeat,'project':project,'workerExit':result.returncode})
            (batch/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
            print(f'반복 {repeat}/3 완료, 실행 종료 코드 {result.returncode}',flush=True)
            if result.returncode:
                raise SystemExit('도구 실행 실패. execution.log 확인 필요')
        finally:
            subprocess.run(command+['stop'],check=False,env=env)
    manifest['finishedAt']=datetime.now(timezone.utc).isoformat()
    (batch/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(f'3회 반복 완료: {batch}',flush=True)
    return batch


if __name__=='__main__':
    if sys.argv[1:]==['--worker']:
        worker()
    else:
        if len(sys.argv)==3 and sys.argv[1]=='--create-after':
            previous=Path(sys.argv[2])/'manifest.json'
            print('선행 반복 측정 종료 후 생성 전용 반복 실행 대기',flush=True)
            deadline=time.time()+3600
            while 'finishedAt' not in json.loads(previous.read_text()):
                if time.time()>deadline:
                    raise SystemExit('선행 측정 종료 대기 시간 초과')
                time.sleep(5)
            os.environ['RENTAL_CREATE_ONLY']='1'
        elif sys.argv[1:]:
            raise SystemExit('사용법: repeat.py [--create-after <이전 원본 폴더>]')
        if os.environ.get('RENTAL_CREATE_ONLY')=='1':
            main()
        else:
            os.environ['RENTAL_CORE_ONLY']='1'
            main()
            os.environ.pop('RENTAL_CORE_ONLY',None)
            os.environ['RENTAL_CREATE_ONLY']='1'
            main()
