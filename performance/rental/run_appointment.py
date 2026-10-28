"""전용 DB에서 같은 약속 제안의 수락·철회 경쟁과 최종 상태 검증."""

from datetime import datetime, timezone
import hashlib
import json
import os
import secrets
import subprocess
import time
import urllib.request
import uuid

from run import BASE, HERE, LOCAL, ROOT, sql


def main():
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
        raise SystemExit('최초 목록 측정용 회원 준비 필요')
    first_item = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,995000) FROM items;'))
    first_rental = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,995000) FROM rentals;'))
    first_slot = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,995000) FROM appointment_slots;'))
    first_proposal = int(sql('SELECT GREATEST(COALESCE(MAX(id),0)+1,995000) FROM appointment_proposals;'))
    statements = ["SET @now=UTC_TIMESTAMP(6); SET @day=DATE(CONVERT_TZ(@now,'+00:00','+09:00')); START TRANSACTION;"]
    cases = []
    for n in range(20):
        item, rental, slot, proposal = first_item+n, first_rental+n, first_slot+2*n, first_proposal+n
        statements.append(f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item},981001,980000,(SELECT id FROM categories WHERE code='LIFE'),980000,'약속 경합 물건 {n}',@day,@day+INTERVAL 30 DAY,'PUBLIC',0,@now,@now);")
        statements.append(f"INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,approved_at,version,created_at,updated_at) VALUES ({rental},{item},981001,981002,980000,@day+INTERVAL 2 DAY,@day+INTERVAL 3 DAY,'APPROVED',@now+INTERVAL 1 DAY,@now+INTERVAL 4 DAY,'약속 경합 물건 {n}','대여 성능 장소',980000,@now,1,@now,@now);")
        statements.append(f"INSERT INTO rental_status_histories (rental_id,actor_id,from_status,to_status,rental_version,created_at,updated_at) VALUES ({rental},981002,NULL,'REQUESTED',0,@now,@now),({rental},981001,'REQUESTED','APPROVED',1,@now,@now);")
        statements.append(f"INSERT INTO appointment_slots (id,rental_id,kind,version,created_at,updated_at) VALUES ({slot},{rental},'PICKUP',1,@now,@now),({slot+1},{rental},'RETURN',0,@now,@now);")
        statements.append(f"INSERT INTO appointment_proposals (id,slot_id,proposer_id,place_id,scheduled_at,place_name_snapshot,status,created_at,updated_at) VALUES ({proposal},{slot},981001,980000,TIMESTAMP(@day+INTERVAL 2 DAY)+INTERVAL 3 HOUR,'대여 성능 장소','PROPOSED',@now,@now);")
        cases.append({'proposalId': proposal, 'slotId': slot, 'keys': {action: str(uuid.uuid4()) for action in ('accept','withdraw')}})
    users = {}
    hashes = []
    for action, member in [('accept',981002),('withdraw',981001)]:
        token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        digest = hashlib.sha256(token.encode()).hexdigest()
        hashes.append(digest)
        users[action] = {'token': token, 'csrf': csrf}
        statements.append(f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES ({member},'{digest}','{csrf}',@now,@now+INTERVAL 2 HOUR,@now+INTERVAL 2 HOUR,@now,@now);")
    statements.append('COMMIT;')
    sql('\n'.join(statements))
    fixture = LOCAL / 'appointment-fixture.json'
    try:
        for user in users.values():
            req = urllib.request.Request(BASE+'/api/v1/auth/csrf',headers={'Cookie':'BILLIM_SESSION='+user['token']})
            with urllib.request.urlopen(req,timeout=10) as response:
                user['csrf'] = json.load(response)['csrfToken']
        fixture.write_text(json.dumps({'users': users,'cases': cases}))
        fixture.chmod(0o600)
        env = dict(os.environ,K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
        with (LOCAL/'appointment-race.log').open('w') as output:
            result = subprocess.run(['k6','run','--summary-export',str(LOCAL/'appointment-race.json'),str(HERE/'appointment-race.js')],env=env,stdout=output,stderr=subprocess.STDOUT)
        records = sql(f"SELECT s.id,s.version,COALESCE(s.confirmed_proposal_id,0),p.id,p.status,COALESCE(p.accepted_by,0),r.status,(SELECT COUNT(*) FROM outbox_events e WHERE e.aggregate_type='APPOINTMENT_SLOT' AND e.aggregate_id=s.id AND e.aggregate_version=2 AND e.event_type=IF(p.status='CONFIRMED','APPOINTMENT_CONFIRMED','APPOINTMENT_WITHDRAWN')) FROM appointment_slots s JOIN appointment_proposals p ON p.slot_id=s.id JOIN rentals r ON r.id=s.rental_id WHERE p.id BETWEEN {first_proposal} AND {first_proposal+19} ORDER BY s.id;")
        rows = [line.split('\t') for line in records.splitlines()]
        def valid_row(row):
            return row[1]=='2' and row[6]=='APPROVED' and row[7]=='1' and (
                (row[4]=='CONFIRMED' and row[2]==row[3] and row[5]=='981002') or
                (row[4]=='WITHDRAWN' and row[2]=='0' and row[5]=='0'))
        operations = ','.join("'POST /api/v1/appointments/"+str(c['proposalId'])+'/'+action+"'" for c in cases for action in ('accept','withdraw'))
        record_count = int(sql(f"SELECT COUNT(*) FROM idempotency_records WHERE operation_key IN ({operations});"))
        event_count = int(sql(f"SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='APPOINTMENT_SLOT' AND aggregate_id BETWEEN {first_slot} AND {first_slot+39};"))
        valid = len(rows)==20 and all(valid_row(row) for row in rows) and record_count==20 and event_count==20
        verification = {
            'finishedAt': datetime.now(timezone.utc).isoformat(),
            'commit': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
            'cases': 20,'requests': 40,'concurrentRequestsPerCase': 2,
            'confirmed': sum(row[4]=='CONFIRMED' for row in rows),
            'withdrawn': sum(row[4]=='WITHDRAWN' for row in rows),
            'columns': ['slotId','version','confirmedProposalId','proposalId','proposalStatus','acceptedBy','rentalStatus','matchingEvents'],
            'rows': rows,'idempotencyRecords': record_count,'changeEvents': event_count,
            'databaseChecksPassed': valid,'k6ExitCode': result.returncode,
        }
        (LOCAL/'appointment-verification.json').write_text(json.dumps(verification,indent=2)+'\n')
        if result.returncode or not valid:
            raise SystemExit('약속 경합 검증 실패. local/appointment-race.log 및 DB 결과 확인 필요')
        print('약속 20개·요청 40건, 응답 및 DB 정합성 검증 통과')
    finally:
        digest_list = ','.join("'"+h+"'" for h in hashes)
        sql(f'UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash IN ({digest_list});')
        fixture.unlink(missing_ok=True)
        print('임시 세션 2개 폐기·인증 fixture 삭제 완료')


if __name__=='__main__':
    main()
