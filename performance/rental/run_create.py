"""전용 DB에서 여러 계정의 신규 대여 생성 부하·생성 멱등성 검증."""

from datetime import datetime, timezone
import hashlib
import json
import os
import secrets
import subprocess
import sys
import time
import urllib.request
import uuid

from run import BASE, HERE, LOCAL, ROOT, sql


def main(replay_only=False):
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    for attempt in range(60):
        try:
            with urllib.request.urlopen(BASE+'/api/v1/auth/csrf',timeout=3) as response:
                if response.status==200:
                    break
        except OSError:
            time.sleep(2)
    else:
        raise SystemExit('전용 백엔드 기동 대기 시간 초과')
    if sql('SELECT DATABASE();')!='billim_rental_perf':
        raise SystemExit('전용 DB 확인 실패')
    if int(sql("SELECT COUNT(*) FROM community_memberships WHERE member_id BETWEEN 981000 AND 981020 AND community_id=980000 AND status='ACTIVE' AND verification_expires_at>UTC_TIMESTAMP();"))!=21:
        raise SystemExit('전용 회원 21명의 유효 소속 준비 필요')
    first = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,1000000) FROM items;'))
    dates = sql("SELECT DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+09:00'))+INTERVAL 2 DAY,DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+09:00'))+INTERVAL 3 DAY,DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+09:00'))+INTERVAL 4 DAY;").split('\t')
    phases = {}
    statements = ['SET @now=UTC_TIMESTAMP(6); START TRANSACTION;']
    next_item = first
    phase_counts=[('replay',1)] if replay_only else [('smoke',1),('warmup',40),('baseline',200),('replay',1)]
    for phase, count in phase_counts:
        groups = []
        for vu in range(10):
            entries = []
            for n in range(count):
                item = next_item
                next_item += 1
                statements.append(f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item},{981011+vu},980000,(SELECT id FROM categories WHERE code='LIFE'),980000,'생성 측정 물건 {item}','{dates[0]}','{dates[2]}','PUBLIC',0,@now,@now);")
                entries.append({'itemId':item,'key':str(uuid.uuid4())})
            groups.append(entries)
        phases[phase] = [g[0] for g in groups] if phase=='replay' else groups
    users, hashes = [], []
    for vu in range(10):
        token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        digest = hashlib.sha256(token.encode()).hexdigest()
        hashes.append(digest)
        users.append({'memberId':981000+vu,'token':token,'csrf':csrf})
        statements.append(f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES ({981000+vu},'{digest}','{csrf}',@now,@now+INTERVAL 2 HOUR,@now+INTERVAL 2 HOUR,@now,@now);")
    statements.append('COMMIT;')
    before = sql('SELECT COUNT(*) FROM rentals;')
    sql('\n'.join(statements))
    fixture = LOCAL/'create-fixture.json'
    try:
        for user in users:
            req=urllib.request.Request(BASE+'/api/v1/auth/csrf',headers={'Cookie':'BILLIM_SESSION='+user['token']})
            with urllib.request.urlopen(req,timeout=10) as response:
                user['csrf']=json.load(response)['csrfToken']
        fixture.write_text(json.dumps({'users':users,'phases':phases,'startDate':dates[0],'endDate':dates[1],'changedEndDate':dates[2]}))
        fixture.chmod(0o600)
        results={}
        reset_warmup=os.environ.get('RENTAL_RESET_WARMUP')=='1'
        initial_state=None
        runs=[('replay',1,'')] if replay_only else [('smoke',10,'1s'),('warmup',10,'30s'),('baseline',10,'3m'),('replay',1,'')]
        for phase,vus,duration in runs:
            env=dict(os.environ,PHASE=phase,VUS=str(vus),DURATION=duration,K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
            print(f'{phase}: {vus}명 {duration}',flush=True)
            with (LOCAL/f'create-{phase}.log').open('w') as output:
                result=subprocess.run(['k6','run','--summary-export',str(LOCAL/f'create-{phase}.json'),str(HERE/'create-rental.js')],env=env,stdout=output,stderr=subprocess.STDOUT)
            results[phase]=result.returncode
            if not (LOCAL/f'create-{phase}.json').exists():
                raise SystemExit(f'{phase} 실행 결과 없음. 측정 중단')
            print(f'{phase}: '+('통과' if result.returncode==0 else '검증 실패 기록, 후속 측정 계속'),flush=True)
            if phase=='warmup' and reset_warmup:
                if not os.environ.get('RENTAL_PERF_PROJECT','').startswith('billim-rental-repeat-'):
                    raise SystemExit('준비 데이터 정리는 새 반복 측정 전용 프로젝트에서만 허용')
                warm_entries=[e for p in ('smoke','warmup') for group in phases[p] for e in group]
                ids=','.join(str(e['itemId']) for e in warm_entries)
                warm_keys=','.join("'"+e['key']+"'" for e in warm_entries)
                cleanup=f"""START TRANSACTION;
DELETE FROM outbox_events WHERE aggregate_type='RENTAL' AND aggregate_id IN (SELECT id FROM rentals WHERE item_id IN ({ids}));
DELETE FROM idempotency_records WHERE operation_key='POST /api/v1/rentals' AND request_key IN ({warm_keys});
DELETE FROM rental_status_histories WHERE rental_id IN (SELECT id FROM rentals WHERE item_id IN ({ids}));
DELETE FROM rentals WHERE item_id IN ({ids});
COMMIT;"""
                sql(cleanup)
                initial_state=[int(v) for v in sql('SELECT (SELECT COUNT(*) FROM members),(SELECT COUNT(*) FROM items),(SELECT COUNT(*) FROM rentals),(SELECT COUNT(*) FROM rental_status_histories),(SELECT COUNT(*) FROM outbox_events),(SELECT COUNT(*) FROM idempotency_records);').split('\t')]
                if initial_state!=[21,3420,1000,1000,0,0]:
                    raise SystemExit('생성 본 측정 초기 상태 불일치. 집계 중단')
                print('준비용 거래·이력·사건·멱등 기록 정리, 초기 건수 일치 확인',flush=True)
        metrics={phase:json.loads((LOCAL/f'create-{phase}.json').read_text())['metrics'] for phase in results}
        expected=sum(int(metrics[p]['checks']['passes']) for p in results if p!='replay' and (not reset_warmup or p=='baseline'))+10
        query=f"SELECT COUNT(*),SUM(status='REQUESTED'),SUM(version=0),COUNT(DISTINCT item_id),COUNT(DISTINCT borrower_id) FROM rentals WHERE item_id BETWEEN {first} AND {next_item-1};"
        counts=[int(v) for v in sql(query).split('\t')]
        effects=[int(v) for v in sql(f"SELECT (SELECT COUNT(*) FROM rental_status_histories h JOIN rentals r ON r.id=h.rental_id WHERE r.item_id BETWEEN {first} AND {next_item-1}), (SELECT COUNT(*) FROM outbox_events e JOIN rentals r ON r.id=e.aggregate_id WHERE e.aggregate_type='RENTAL' AND e.event_type='RENTAL_REQUESTED' AND r.item_id BETWEEN {first} AND {next_item-1});").split('\t')]
        keys=[e['key'] for phase,groups in phases.items() for e in (groups if phase=='replay' else [e for group in groups for e in group])]
        key_list=','.join("'"+key+"'" for key in keys)
        record_count=int(sql(f"SELECT COUNT(*) FROM idempotency_records WHERE operation_key='POST /api/v1/rentals' AND request_key IN ({key_list});"))
        valid=counts==[expected,expected,expected,expected,10] and effects==[expected,expected] and record_count==expected
        replay_items=','.join(str(e['itemId']) for e in phases['replay'])
        replay_rows=sql(f"SELECT item_id,COUNT(*) FROM rentals WHERE item_id IN ({replay_items}) GROUP BY item_id;")
        valid=valid and len(replay_rows.splitlines())==10 and all(row.split('\t')[1]=='1' for row in replay_rows.splitlines())
        verification={
            'finishedAt':datetime.now(timezone.utc).isoformat(),
            'commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'databaseRentalsBefore':int(before),'preparedItems':next_item-first,'users':10,'ownerAccounts':10,
            'expectedCreates':expected,'actualCreates':counts[0],'requestedCount':counts[1],'versionZeroCount':counts[2],
            'uniqueItems':counts[3],'uniqueBorrowers':counts[4],'histories':effects[0],'events':effects[1],
            'idempotencyRecords':record_count,'replayUniqueRentals':10 if valid else None,
            'databaseChecksPassed':valid,'k6ExitCodes':results,
            'allThresholdsPassed':all(code==0 for code in results.values()),
            'warmupDataReset':reset_warmup,'beforeBaselineState':initial_state,
        }
        (LOCAL/'create-verification.json').write_text(json.dumps(verification,indent=2)+'\n')
        if not valid:
            raise SystemExit('대여 생성 DB 정합성 검증 실패')
        print(f'정상 생성 {expected}건·이력·이벤트·멱등 기록 일치. 성능 기준 통과 여부는 각 구간 결과 확인.',flush=True)
        if any(code!=0 for code in results.values()):
            raise SystemExit('응답 또는 성능 기준 실패. 원본 결과와 DB 후검증 함께 확인 필요')
    finally:
        digest_list=','.join("'"+h+"'" for h in hashes)
        sql(f'UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash IN ({digest_list});')
        fixture.unlink(missing_ok=True)
        print('임시 세션 10개 폐기·인증 fixture 삭제 완료',flush=True)


if __name__=='__main__':
    if sys.argv[1:] not in ([],['--replay-only']):
        raise SystemExit('사용법: python3 performance/rental/run_create.py [--replay-only]')
    main(bool(sys.argv[1:]))
