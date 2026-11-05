"""메시지·대여·알림이 있는 격리 DB의 버전별 목록 성능 측정."""
import json
import os
import subprocess
import time
import urllib.request
from pathlib import Path

import seed

HERE = Path('/scripts')
PROJECT = 'billim-perf-rich'
VERSION = os.environ.get('RICH_VERSION', 'baseline')
COMPOSE = HERE / 'compose.rich.yaml'
OUT = HERE / 'local'


def run(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def compose(*args, **kwargs):
    return run('docker', 'compose', '-p', PROJECT, '-f', str(COMPOSE), *args, **kwargs)


def sql(statement):
    return compose('exec', '-T', 'mysql', 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot billim_perf',
                   input=statement, capture_output=True).stdout


def request(path, token=None):
    headers = {'Cookie': f'BILLIM_SESSION={token}'} if token else {}
    with urllib.request.urlopen(urllib.request.Request('http://backend:8080' + path, headers=headers), timeout=60) as response:
        return json.load(response)


def enrich():
    fixture = json.loads(seed.OUTPUT.read_text())
    statements = ['START TRANSACTION;']
    for pair in fixture['pairs']:
        room = pair['roomId']
        owner, borrower = pair['owner']['memberId'], pair['requester']['memberId']
        for offset, status in enumerate(('REQUESTED', 'APPROVED', 'ACTIVE', 'RETURNED', 'CANCELED')):
            start = 5 if status == 'REQUESTED' else (-10 if status in ('RETURNED', 'CANCELED') else 0)
            end = start + 2
            statements.append(f"INSERT INTO rentals (item_id,owner_id,borrower_id,community_id,chat_room_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,version,created_at,updated_at) SELECT item_id,{owner},{borrower},910000,id,DATE_ADD(UTC_DATE(),INTERVAL {start} DAY),DATE_ADD(UTC_DATE(),INTERVAL {end} DAY),'{status}',DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(),INTERVAL {end} DAY),'Performance Test Item','Performance Test Place',910000,0,DATE_SUB(UTC_TIMESTAMP(),INTERVAL {offset} DAY),UTC_TIMESTAMP() FROM chat_rooms WHERE id={room};")
        for sequence in range(1, 201):
            sender = owner if sequence % 2 else borrower
            statements.append(f"INSERT INTO messages (room_id,sequence,sender_id,type,body,client_message_id,created_at,updated_at) VALUES ({room},{sequence},{sender},'TEXT','Rich performance message {sequence}',UUID(),DATE_SUB(UTC_TIMESTAMP(),INTERVAL {200-sequence} SECOND),UTC_TIMESTAMP());")
        statements.append(f'UPDATE chat_rooms SET next_sequence=201,last_message_at=UTC_TIMESTAMP() WHERE id={room};')
        statements.append(f'UPDATE chat_participants SET last_read_sequence=100 WHERE room_id={room};')
    statements.append('COMMIT;')
    sql('\n'.join(statements))


def main():
    OUT.mkdir(exist_ok=True)
    result = {'version': VERSION, 'status': 'running', 'measurements': []}
    destination = OUT / f'rich-{VERSION}-result.json'
    try:
        # 호스트 소스를 명시적으로 마운트한 빌드. 측정 서버와 빌드 동시 실행 방지.
        run('docker', 'run', '--rm', '--name', f'{PROJECT}-build-{VERSION}',
            '--mount', f"type=bind,source={os.environ['HOST_BACKEND']},target=/workspace",
            '--mount', 'type=volume,source=billim-rich-gradle,target=/home/gradle/.gradle',
            '--mount', 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
            '-e', 'DOCKER_HOST=unix:///var/run/docker.sock',
            '-e', 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal',
            '-w', '/workspace', 'gradle:8.14-jdk21', 'sh', './gradlew',
            'build' if VERSION != 'baseline' else 'bootJar', '--no-daemon')
        run('docker', 'build', '-t', f'billim-perf-rich:{VERSION}', '/backend')
        compose('up', '-d', 'mysql', 'backend')
        for _ in range(120):
            try:
                request('/api/v1/auth/csrf')
                break
            except Exception:
                time.sleep(2)
        else:
            raise RuntimeError('Backend readiness timeout')
        seed.COMPOSE = COMPOSE
        if not seed.OUTPUT.exists():
            if VERSION != 'baseline':
                raise RuntimeError('Baseline fixture required')
            seed.main()
            enrich()
        else:
            seed.refresh_and_save_csrf(json.loads(seed.OUTPUT.read_text())['pairs'])
        token = json.loads(seed.OUTPUT.read_text())['pairs'][0]['owner']['token']
        result['counts'] = sql('SELECT (SELECT COUNT(*) FROM chat_rooms),(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM rentals),(SELECT COUNT(*) FROM notifications);').strip()
        for size in (1, 20, 50):
            path = f'/api/v1/chat/rooms?filter=ALL&page=0&size={size}'
            body = request(path, token)
            assert len(body['items']) == size
            assert all(len(room['rentals']) == 5 and room['unreadCount'] == 50 for room in body['items'])
            snapshot = OUT / f'rich-{VERSION}-response-{size}.json'
            snapshot.write_text(json.dumps(body, sort_keys=True, ensure_ascii=False))
            if VERSION != 'baseline':
                assert json.loads((OUT / f'rich-baseline-response-{size}.json').read_text()) == body, 'Response changed'
            deltas = []
            for _ in range(3):
                before = int(sql("SHOW GLOBAL STATUS LIKE 'Com_select';").split()[-1])
                request(path, token)
                after = int(sql("SHOW GLOBAL STATUS LIKE 'Com_select';").split()[-1])
                deltas.append(after - before)
            result.setdefault('select_counts', {})[size] = deltas
            for repeat in range(1, 4):
                filename = f'rich-{VERSION}-{size}-{repeat}.json'
                run('docker', 'run', '--rm', '--network', f'{PROJECT}_default',
                    '--volumes-from', os.environ['RUNNER_NAME'], '-w', '/scripts',
                    '-e', 'CASE=chat-room-list', '-e', 'FIXTURE_NAME=fixtures-rich.json',
                    '-e', f'INBOX_PAGE_SIZE={size}', '-e', 'VUS=4', '-e', 'DURATION=30s',
                    'grafana/k6:0.54.0', 'run', '--summary-export', f'/scripts/local/{filename}', '/scripts/chat-notification.js')
                result['measurements'].append(json.loads((OUT / filename).read_text()))
        result['status'] = 'passed'
    except Exception as exc:
        result['status'] = 'failed'
        result['error'] = str(exc)
        raise
    finally:
        destination.write_text(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
