"""전용 로컬 DB에 합성 데이터·2시간 세션 생성 후 목록 성능 측정."""

import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LOCAL = HERE / "local"
COMPOSE = ["docker", "compose", "-f", str(HERE / "compose.yaml")]
BASE = "http://localhost:18081"


def sql(statement):
    result = subprocess.run(COMPOSE + ["exec", "-T", "mysql", "sh", "-c",
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -urental_perf -N -B billim_rental_perf'],
        input=statement, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("전용 DB SQL 실행 실패. 비밀값 보호를 위해 SQL 출력 생략")
    return result.stdout.strip()


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
    if sql("SELECT DATABASE();") != "billim_rental_perf":
        raise SystemExit("전용 DB 확인 실패")
    token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(token.encode()).hexdigest()
    statements = ["SET @now=UTC_TIMESTAMP(6); START TRANSACTION;"]
    count = int(sql("SELECT COUNT(*) FROM rentals;"))
    if count == 0:
        statements += [
            "INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,status,created_at,updated_at) VALUES (980000,'대여 성능 동네','합성 데이터',35,127,1000,'ACTIVE',@now,@now);",
            "INSERT INTO community_places (id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES (980000,980000,'대여 성능 장소',35,127,TRUE,@now,@now);",
        ]
        for i in range(21):
            member = 981000 + i
            statements.append(f"INSERT INTO members (id,display_name,role,status,active_community_id,version,created_at,updated_at) VALUES ({member},'합성 회원 {i}','USER','ACTIVE',980000,0,@now,@now);")
            statements.append(f"INSERT INTO community_memberships (member_id,community_id,status,verified_at,verification_expires_at,created_at,updated_at) VALUES ({member},980000,'ACTIVE',@now,@now+INTERVAL 30 DAY,@now,@now);")
        for i in range(1000):
            item, rental, owner = 982000 + i, 984000 + i, 981001 + i % 20
            status = ['REQUESTED', 'REJECTED', 'CANCELED', 'EXPIRED'][i % 4]
            statements.append(f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item},{owner},980000,(SELECT id FROM categories WHERE code='LIFE'),980000,'합성 물건 {i}',UTC_DATE(),UTC_DATE()+INTERVAL 30 DAY,'PUBLIC',0,@now,@now);")
            statements.append(f"INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,version,created_at,updated_at) VALUES ({rental},{item},{owner},981000,980000,UTC_DATE()+INTERVAL 2 DAY,UTC_DATE()+INTERVAL 3 DAY,'{status}',@now+INTERVAL 1 DAY,@now+INTERVAL 4 DAY,'합성 물건 {i}','대여 성능 장소',980000,0,@now-INTERVAL {i} MINUTE,@now);")
            statements.append(f"INSERT INTO rental_status_histories (rental_id,actor_id,from_status,to_status,rental_version,created_at,updated_at) VALUES ({rental},981000,NULL,'REQUESTED',0,@now,@now);")
    elif count != 1000:
        raise SystemExit("예상 대여 1,000건과 불일치. 측정 중단")
    elif int(sql("SELECT COUNT(*) FROM rentals WHERE status='REQUESTED' AND request_expires_at > UTC_TIMESTAMP(6);")) != 250:
        raise SystemExit("대기 요청이 만료된 기존 데이터. 새 전용 DB에서 동일 조건 재측정 필요")
    statements += [
        "UPDATE auth_sessions SET revoked_at=@now WHERE member_id=981000 AND revoked_at IS NULL;",
        f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES (981000,'{token_hash}','{secrets.token_urlsafe(32)}',@now,@now+INTERVAL 2 HOUR,@now+INTERVAL 2 HOUR,@now,@now);",
        "COMMIT;",
    ]
    sql("\n".join(statements))
    req = urllib.request.Request(BASE + '/api/v1/rentals?role=BORROWER&page=0&size=20',
        headers={'Cookie': 'BILLIM_SESSION=' + token})
    with urllib.request.urlopen(req, timeout=15) as response:
        body = json.load(response)
    if body.get('totalElements') != 1000 or len(body.get('items', [])) != 20:
        raise SystemExit('목록 데이터 검증 실패')
    metadata = {
        'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'members': 21, 'items': 1000, 'rentals': 1000, 'borrowerRentals': 1000,
        'statuses': {'REQUESTED': 250, 'REJECTED': 250, 'CANCELED': 250, 'EXPIRED': 250},
        'role': 'BORROWER', 'page': 0, 'size': 20, 'pauseSeconds': 1,
    }
    (LOCAL / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    env = dict(os.environ, SESSION=token, BASE_URL=BASE,
        K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)')
    try:
        for name, vus, duration in [('smoke', 1, '5s'), ('warmup', 10, '30s'), ('baseline', 10, '3m')]:
            print(f'{name}: VU {vus}, {duration}', flush=True)
            command = ['k6', 'run', '-e', f'VUS={vus}', '-e', f'DURATION={duration}',
                '--summary-export', str(LOCAL / f'{name}.json'), str(HERE / 'rental-list.js')]
            with (LOCAL / f'{name}.log').open('w') as output:
                result = subprocess.run(command, env=env, stdout=output, stderr=subprocess.STDOUT)
            if result.returncode:
                raise SystemExit(f'{name} 실패. local/{name}.log 확인 필요')
            print(f'{name} 통과', flush=True)
    finally:
        sql(f"UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE token_hash='{token_hash}';")
    print('측정 완료. 세션 폐기 완료. 결과: performance/rental/local/', flush=True)


if __name__ == '__main__':
    main()
