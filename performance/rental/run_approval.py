"""전용 DB에서 물건별 10개 요청 동시 승인과 최종 저장 결과 검증."""

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


def main(mode='approval'):
    if mode not in ('approval', 'idempotency'):
        raise SystemExit('지원하지 않는 측정 모드')
    LOCAL.mkdir(mode=0o700, exist_ok=True)
    for attempt in range(60):
        try:
            with urllib.request.urlopen(BASE + '/api/v1/auth/csrf', timeout=3) as response:
                if response.status == 200:
                    break
        except OSError:
            time.sleep(2)
    else:
        raise SystemExit('전용 백엔드 기동 대기 시간 초과')
    if sql('SELECT DATABASE();') != 'billim_rental_perf':
        raise SystemExit('전용 DB 확인 실패')
    if int(sql('SELECT COUNT(*) FROM members WHERE id BETWEEN 981000 AND 981020;')) != 21:
        raise SystemExit('최초 목록 측정용 회원 21명 준비 필요')
    token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    digest = hashlib.sha256(token.encode()).hexdigest()
    first_item = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,990000) FROM items;'))
    first_rental = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,990000) FROM rentals;'))
    waves = []
    statements = ['SET @now=UTC_TIMESTAMP(6); START TRANSACTION;']
    for wave in range(20):
        item = first_item + wave
        statements.append(f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item},981001,980000,(SELECT id FROM categories WHERE code='LIFE'),980000,'동시 승인 물건 {wave}',UTC_DATE(),UTC_DATE()+INTERVAL 30 DAY,'PUBLIC',0,@now,@now);")
        entries = []
        for n in range(10):
            rental, borrower = first_rental + wave * 10 + n, 981002 + n
            statements.append(f"INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,version,created_at,updated_at) VALUES ({rental},{item},981001,{borrower},980000,UTC_DATE()+INTERVAL 2 DAY,UTC_DATE()+INTERVAL 3 DAY,'REQUESTED',@now+INTERVAL 1 DAY,@now+INTERVAL 4 DAY,'동시 승인 물건 {wave}','대여 성능 장소',980000,0,@now,@now);")
            statements.append(f"INSERT INTO rental_status_histories (rental_id,actor_id,from_status,to_status,rental_version,created_at,updated_at) VALUES ({rental},{borrower},NULL,'REQUESTED',0,@now,@now);")
            entries.append({'rentalId': rental, 'key': str(uuid.uuid4())})
        waves.append(entries)
    statements.append(f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES (981001,'{digest}','{csrf}',@now,@now+INTERVAL 2 HOUR,@now+INTERVAL 2 HOUR,@now,@now);")
    statements.append('COMMIT;')
    sql('\n'.join(statements))
    fixture_path = LOCAL / 'approval-fixture.json'
    try:
        req = urllib.request.Request(BASE + '/api/v1/auth/csrf', headers={'Cookie': 'BILLIM_SESSION=' + token})
        with urllib.request.urlopen(req, timeout=10) as response:
            csrf = json.load(response)['csrfToken']
        fixture_path.write_text(json.dumps({'token': token, 'csrf': csrf, 'waves': waves}))
        fixture_path.chmod(0o600)
        env = dict(os.environ, K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
        script = 'idempotency.js' if mode == 'idempotency' else 'concurrent-approval.js'
        with (LOCAL / f'{mode}.log').open('w') as output:
            result = subprocess.run(['k6','run','--summary-export',str(LOCAL / f'{mode}.json'),
                str(HERE / script)],env=env,stdout=output,stderr=subprocess.STDOUT)
        last = first_rental + 199
        grouped = sql(f"SELECT item_id,SUM(status='APPROVED'),SUM(status='REQUESTED') FROM rentals WHERE id BETWEEN {first_rental} AND {last} GROUP BY item_id ORDER BY item_id;")
        rows = [[int(value) for value in row.split('\t')] for row in grouped.splitlines()]
        side_effects = sql(f"SELECT (SELECT COUNT(*) FROM rental_status_histories WHERE rental_id BETWEEN {first_rental} AND {last} AND to_status='APPROVED'),(SELECT COUNT(*) FROM appointment_slots WHERE rental_id BETWEEN {first_rental} AND {last}),(SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='RENTAL' AND aggregate_id BETWEEN {first_rental} AND {last} AND event_type='RENTAL_APPROVED');")
        counts = [int(v) for v in side_effects.split('\t')]
        valid = len(rows) == 20 and all(row[1:] == [1,9] for row in rows) and counts == [20,40,20]
        versions = int(sql(f"SELECT COUNT(*) FROM rentals WHERE id BETWEEN {first_rental} AND {last} AND status='APPROVED' AND version=1;"))
        operations = ','.join("'POST /api/v1/rentals/" + str(w[0]['rentalId']) + "/approve'" for w in waves)
        record_count = int(sql(f"SELECT COUNT(*) FROM idempotency_records WHERE member_id=981001 AND operation_key IN ({operations});")) if mode == 'idempotency' else None
        if mode == 'idempotency':
            valid = valid and versions == 20 and record_count == 20
        verification = {
            'finishedAt': datetime.now(timezone.utc).isoformat(),
            'commit': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'mode': mode, 'waves': 20, 'concurrentRequestsPerWave': 10,
            'requestCount': 240 if mode == 'idempotency' else 200,
            'itemResults': rows, 'approvalHistories': counts[0], 'appointmentSlots': counts[1],
            'approvalEvents': counts[2], 'databaseChecksPassed': valid, 'k6ExitCode': result.returncode,
            'approvedVersionOneCount': versions, 'idempotencyRecordCount': record_count,
        }
        (LOCAL / f'{mode}-verification.json').write_text(json.dumps(verification,indent=2)+'\n')
        if result.returncode or not valid:
            raise SystemExit(f'{mode} 검증 실패. local/{mode}.log 및 검증 결과 확인 필요')
        print(f'{mode} 요청·응답 및 DB 정합성 검증 통과')
    finally:
        sql(f"UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash='{digest}';")
        fixture_path.unlink(missing_ok=True)
        print('임시 세션 폐기·인증 fixture 삭제 완료')


if __name__ == '__main__':
    if sys.argv[1:] not in ([], ['--idempotency']):
        raise SystemExit('사용법: python3 performance/rental/run_approval.py [--idempotency]')
    main('idempotency' if sys.argv[1:] else 'approval')
