"""전용 MySQL 목록의 변경 전후 비교. 인증값·실제 환경 파일 접근 금지."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import threading
import time
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PROJECT = os.environ.get('RENTAL_LIST_PROJECT', 'billim-rental-list-20261031')
COMPOSE = ['docker', 'compose', '-f', str(HERE/'compose.yaml'), '-f', str(HERE/'list-compare.yaml'), '-p', PROJECT]
LOCAL = Path(os.environ.get('RENTAL_LIST_LOCAL', HERE/'local/list-compare'))
RAW = Path(os.environ.get('RENTAL_LIST_RAW', ROOT/'docs/performance/rental/raw/list-compare-20261031'))
FIXTURE = LOCAL/'fixture.json'

def sql(statement):
    result = subprocess.run(COMPOSE+['exec','-T','mysql','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B billim_rental_perf'],
        input=statement,text=True,capture_output=True)
    if result.returncode:
        raise RuntimeError('전용 측정 DB SQL 실패. 비밀값 보호를 위해 SQL 원문·출력 생략')
    return result.stdout.strip()

def request(path, token):
    req = urllib.request.Request('http://localhost:18081'+path, headers={'Cookie':'BILLIM_SESSION='+token})
    with urllib.request.urlopen(req, timeout=20) as response:
        return json.load(response)

def snapshot():
    data = sql("SELECT EVENT_NAME,SUM(COUNT_STAR) FROM performance_schema.events_statements_summary_by_account_by_event_name WHERE USER='rental_perf' AND EVENT_NAME IN ('statement/sql/select','statement/sql/insert','statement/sql/update','statement/sql/delete') GROUP BY EVENT_NAME;")
    return {parts[0].rsplit('/',1)[1]:int(parts[1]) for line in data.splitlines() if (parts:=line.split('\t'))}

def digests():
    data = sql("SELECT DIGEST,DIGEST_TEXT,COUNT_STAR FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='billim_rental_perf' AND DIGEST_TEXT LIKE 'SELECT%' AND DIGEST_TEXT NOT LIKE '%performance_schema%';")
    return {parts[0]:{'sql':parts[1],'count':int(parts[2])} for line in data.splitlines() if len(parts:=line.split('\t'))==3}

def scrape():
    shell="exec 3<>/dev/tcp/127.0.0.1/9090; printf 'GET /actuator/prometheus HTTP/1.0\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3; cat <&3"
    result=subprocess.run(COMPOSE+['exec','-T','backend','bash','-c',shell],capture_output=True,text=True,timeout=10)
    if result.returncode or not result.stdout.startswith('HTTP/1.1 200'):raise RuntimeError('Actuator 수집 실패')
    prefixes=('process_cpu_usage','jvm_memory_used_bytes','hikaricp_connections_active','tomcat_threads_busy_threads')
    return [line for line in result.stdout.splitlines() if line.startswith(prefixes)]

def wait_ready():
    for _ in range(60):
        try:
            with urllib.request.urlopen('http://localhost:18081/api/v1/auth/csrf',timeout=3) as response:
                if response.status==200:return
        except OSError:time.sleep(1)
    raise RuntimeError('전용 백엔드 준비 대기 초과')

def prepare():
    wait_ready()
    if sql('SELECT DATABASE();') != 'billim_rental_perf':raise RuntimeError('전용 DB 확인 실패')
    if sql('SELECT COUNT(*) FROM rentals;') != '0':raise RuntimeError('빈 전용 DB만 초기화 가능. 기존 데이터 보존')
    LOCAL.mkdir(parents=True,exist_ok=True,mode=0o700)
    RAW.mkdir(parents=True,exist_ok=True)
    statements=["SET @now=UTC_TIMESTAMP(6); START TRANSACTION;",
        "INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,status,created_at,updated_at) VALUES (910000,'목록 측정 동네','합성 데이터',35,127,1000,'ACTIVE',@now,@now);",
        "INSERT INTO community_places (id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES (910000,910000,'목록 측정 장소',35,127,TRUE,@now,@now);"]
    for member in [910000,910001]+list(range(910010,910050)):
        statements += [f"INSERT INTO members (id,display_name,role,status,active_community_id,version,created_at,updated_at) VALUES ({member},'합성 회원 {member}','USER','ACTIVE',910000,0,@now,@now);",
            f"INSERT INTO community_memberships (member_id,community_id,status,verified_at,verification_expires_at,created_at,updated_at) VALUES ({member},910000,'ACTIVE',@now,@now+INTERVAL 30 DAY,@now,@now);"]
    for role in ['borrowed','owner-requested']:
        for i in range(1000):
            item=(920000 if role=='borrowed' else 930000)+i
            rental=(940000 if role=='borrowed' else 950000)+i
            owner=910010+i%20 if role=='borrowed' else 910001
            borrower=910000 if role=='borrowed' else 910030+i%20
            status=['REQUESTED','REJECTED','CANCELED','EXPIRED','APPROVED','ACTIVE','RETURNED'][i%7] if role=='borrowed' else 'REQUESTED'
            statements += [f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item},{owner},910000,(SELECT id FROM categories WHERE code='LIFE'),910000,'합성 물건 {item}',UTC_DATE(),UTC_DATE()+INTERVAL 30 DAY,'PUBLIC',0,@now,@now);",
                f"INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,version,created_at,updated_at) VALUES ({rental},{item},{owner},{borrower},910000,UTC_DATE()+INTERVAL 2 DAY,UTC_DATE()+INTERVAL 3 DAY,'{status}',@now+INTERVAL 1 DAY,@now+INTERVAL 4 DAY,'합성 물건 {item}','목록 측정 장소',910000,0,@now-INTERVAL {i} MINUTE,@now);"]
    cases={}
    for label,member,path in [('borrowed',910000,'/api/v1/rentals?role=BORROWER&page=0&size=20'),('owner-requested',910001,'/api/v1/rentals?role=OWNER&status=REQUESTED&page=0&size=20')]:
        token=secrets.token_urlsafe(32)
        token_hash=hashlib.sha256(token.encode()).hexdigest()
        statements.append(f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES ({member},'{token_hash}','{secrets.token_urlsafe(32)}',@now,@now+INTERVAL 4 HOUR,@now+INTERVAL 4 HOUR,@now,@now);")
        cases[label]={'path':path,'token':token}
    statements.append('COMMIT;')
    sql('\n'.join(statements))
    for label,entry in cases.items():
        entry['expected']=request(entry['path'],entry['token'])
        if entry['expected']['totalElements']!=1000 or len(entry['expected']['items'])!=20:raise RuntimeError('합성 데이터 검증 실패')
        if label=='owner-requested' and any(r['allowedActions']!=['APPROVE','REJECT'] for r in entry['expected']['items']):raise RuntimeError('승인 가능 요청 구성 실패')
    FIXTURE.write_text(json.dumps({'cases':cases},ensure_ascii=False))
    FIXTURE.chmod(0o600)
    (RAW/'fixture-metadata.json').write_text(json.dumps({'project':PROJECT,'members':42,'items':2000,'rentals':2000,'cases':{label:{'path':entry['path'],'expected':entry['expected']} for label,entry in cases.items()}},ensure_ascii=False,indent=2)+'\n')
    print('합성 데이터·전용 세션 준비 완료',flush=True)

def observe(label,case,duration,stem):
    target=RAW/label/case
    target.mkdir(parents=True,exist_ok=True)
    summary=target/(stem+'.json')
    before=snapshot()
    env=dict(os.environ,RENTAL_LIST_FIXTURE=str(FIXTURE),RENTAL_LIST_CASE=case,DURATION=duration,K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
    command=['k6','run','--summary-export',str(summary),str(ROOT/'docs/performance/scripts/rental-list-compare.js')]
    started=time.time()
    readings=[]; scrape_errors=[]; done=threading.Event()
    def collect():
        while not done.is_set():
            try:readings.append({'at':time.time(),'metrics':scrape()})
            except Exception as exc:scrape_errors.append(type(exc).__name__)
            done.wait(3)
    sampler=threading.Thread(target=collect,daemon=True);sampler.start()
    with (target/(stem+'.log')).open('w') as log:
        result=subprocess.run(command,env=env,stdout=log,stderr=subprocess.STDOUT)
    done.set();sampler.join(timeout=12)
    after=snapshot()
    metrics=json.loads(summary.read_text())
    requests=metrics['metrics']['http_reqs']['count']
    delta={key:after.get(key,0)-before.get(key,0) for key in set(before)|set(after)}
    observation={'elapsedSeconds':time.time()-started,'sqlDelta':delta,'requests':requests,'sqlPerRequest':sum(delta.values())/requests,'k6ExitCode':result.returncode,'actuatorSamples':readings,'actuatorErrors':scrape_errors}
    (target/(stem+'-sql.json')).write_text(json.dumps(observation,indent=2)+'\n')
    if result.returncode:raise RuntimeError(f'{case} {stem} 검증 실패. 원본 로그 확인 필요')
    print(f'{label} {case} {stem} 완료: 요청 {requests}, SQL/요청 {observation["sqlPerRequest"]:.2f}',flush=True)

def measure(label):
    wait_ready()
    cases=json.loads(FIXTURE.read_text())['cases']
    for case,entry in cases.items():
        if request(entry['path'],entry['token'])!=entry['expected']:raise RuntimeError('응답·허용 행동 변경 감지')
        before=digests();counts_before=snapshot()
        request(entry['path'],entry['token'])
        counts_after=snapshot();after=digests()
        diagnostic={'accountSqlDelta':{key:counts_after.get(key,0)-counts_before.get(key,0) for key in set(counts_before)|set(counts_after)},'statements':[{'sql':v['sql'],'calls':v['count']-before.get(key,{}).get('count',0)} for key,v in after.items() if v['count']>before.get(key,{}).get('count',0)]}
        folder=RAW/label/case;folder.mkdir(parents=True,exist_ok=True)
        (folder/'diagnostic.json').write_text(json.dumps(diagnostic,ensure_ascii=False,indent=2)+'\n')
        observe(label,case,'10s','warmup')
        for repeat in range(1,4):observe(label,case,'30s',f'run-{repeat}')
    jar=ROOT/'backend/build/libs/billim-0.0.1-SNAPSHOT.jar'
    sources=['backend/src/main/java/com/billim/domain/rental/service/RentalQueryService.java',
        'backend/src/main/java/com/billim/domain/rental/repository/RentalRepository.java']
    (RAW/label/'metadata.json').write_text(json.dumps({'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
        'sourceSha256':{path:hashlib.sha256((ROOT/path).read_bytes()).hexdigest() for path in sources},
        'workingTreeChanges':bool(subprocess.check_output(['git','diff','--',*sources],cwd=ROOT)),
        'duration':'30s','warmup':'10s','vus':10,'sleepSeconds':0.2,'cases':list(cases)},indent=2)+'\n')

if __name__=='__main__':
    if sys.argv[1:]==['prepare']:prepare()
    elif len(sys.argv)==2 and sys.argv[1] in ('before','after'):measure(sys.argv[1])
    elif sys.argv[1:]==['revoke']:
        sql("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE member_id IN (910000,910001) AND revoked_at IS NULL;")
        print('전용 측정 세션 폐기 완료')
    elif sys.argv[1:]==['probe']:
        before=snapshot();digest_before=digests();readings=scrape();digest_after=digests();after=snapshot()
        result={'sqlDelta':{key:after.get(key,0)-before.get(key,0) for key in set(before)|set(after)},
            'actuatorMetricLines':len(readings),'statements':[{'sql':v['sql'],'calls':v['count']-digest_before.get(key,{}).get('count',0)} for key,v in digest_after.items() if v['count']>digest_before.get(key,{}).get('count',0)]}
        (RAW/'actuator-probe.json').write_text(json.dumps(result,indent=2)+'\n')
        print('Actuator 계측 자체 SQL',result['sqlDelta'])
    else:raise SystemExit('사용법: compare_list.py prepare|before|after|probe|revoke')
