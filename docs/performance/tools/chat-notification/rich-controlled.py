"""고정 워밍업·MySQL/JVM 재시작·ABBA 순서의 동일 데이터 비교. 기존 결과 덮어쓰기 금지."""
import json
import os
import subprocess
import time
from pathlib import Path

import importlib.util

HERE = Path('/scripts')
OUT = HERE / 'local'
spec = importlib.util.spec_from_file_location('rich_benchmark', HERE / 'rich-benchmark.py')
benchmark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(benchmark)
PROJECT = benchmark.PROJECT
LABEL = os.environ['CONTROLLED_LABEL']
if not LABEL.replace('-', '').isalnum():
    raise ValueError('Invalid controlled run label')


def compose(*args, **kwargs):
    return benchmark.run('docker', 'compose', '-p', PROJECT,
        '-f', str(HERE / 'compose.rich.yaml'),
        '-f', str(HERE / 'compose.rich.controlled.yaml'), *args, **kwargs)


benchmark.compose = compose


def k6(size, warm, filename):
    benchmark.run('docker', 'run', '--rm', '--network', f'{PROJECT}_default',
        '--volumes-from', os.environ['RUNNER_NAME'], '-w', '/scripts',
        '-e', f'PAGE_SIZE={size}', '-e', f'WARM={str(warm).lower()}',
        'grafana/k6:0.54.0', 'run', '--summary-export', f'/scripts/local/{filename}',
        '/scripts/rich-controlled.js')
    return json.loads((OUT / filename).read_text())


def main():
    destination = OUT / f'{LABEL}-result.json'
    if destination.exists():
        raise RuntimeError('Result already exists; use a new label')
    fixture = json.loads((OUT / 'fixtures-rich.json').read_text())
    token = fixture['pairs'][0]['owner']['token']
    members = sorted({user['memberId'] for pair in fixture['pairs']
                      for user in (pair['owner'], pair['requester'])})
    member_ids = ','.join(str(int(member)) for member in members)
    result = {'status': 'running', 'order': ['baseline', 'optimized', 'optimized', 'baseline'] * 2,
              'warmup_requests_per_size': 300, 'vus': 4, 'duration': '30s', 'trials': []}
    reference = {}
    try:
        result['images'] = {version: benchmark.run('docker', 'image', 'inspect',
            f'billim-perf-rich:{version}', '--format', '{{.Id}}', capture_output=True).stdout.strip()
            for version in ('baseline', 'optimized')}
        for trial, version in enumerate(result['order'], 1):
            os.environ['RICH_VERSION'] = version
            # 두 프로세스의 캐시를 회차별 재생성. OS 파일 캐시는 유지.
            compose('stop', 'backend', 'mysql')
            compose('up', '-d', '--force-recreate', 'mysql')
            compose('up', '-d', '--force-recreate', 'backend')
            for _ in range(120):
                try:
                    benchmark.request('/api/v1/auth/csrf')
                    break
                except Exception:
                    time.sleep(2)
            else:
                raise RuntimeError('Backend readiness timeout')
            # 격리 시험 세션의 만료만 연장. 메시지·대여·알림 데이터 변경 없음.
            benchmark.sql(f"UPDATE auth_sessions SET last_seen_at=UTC_TIMESTAMP(6),expires_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 6 HOUR),absolute_expires_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 6 HOUR) WHERE member_id IN ({member_ids});")
            counts = benchmark.sql('SELECT (SELECT COUNT(*) FROM chat_rooms),(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM rentals),(SELECT COUNT(*) FROM notifications);').strip()
            if counts != '50\t10000\t250\t5000':
                raise RuntimeError('Unexpected business data counts')
            for size in (1, 20, 50):
                k6(size, True, f'{LABEL}-{trial}-{version}-{size}-warm.json')
            entry = {'trial': trial, 'version': version, 'counts': counts, 'sizes': {}}
            for size in (1, 20, 50):
                path = f'/api/v1/chat/rooms?filter=ALL&page=0&size={size}'
                response = benchmark.request(path, token)
                assert len(response['items']) == size
                assert all(len(room['rentals']) == 5 and room['unreadCount'] == 50 for room in response['items'])
                if size not in reference:
                    reference[size] = response
                    (OUT / f'{LABEL}-reference-{size}.json').write_text(json.dumps(response, ensure_ascii=False))
                else:
                    assert response == reference[size], f'Response mismatch in trial {trial}, size {size}'
                deltas = []
                for _ in range(3):
                    before = int(benchmark.sql("SHOW GLOBAL STATUS LIKE 'Com_select';").split()[-1])
                    benchmark.request(path, token)
                    after = int(benchmark.sql("SHOW GLOBAL STATUS LIKE 'Com_select';").split()[-1])
                    deltas.append(after - before)
                filename = f'{LABEL}-{trial}-{version}-{size}.json'
                summary = k6(size, False, filename)['metrics']
                entry['sizes'][size] = {'sql': deltas, 'duration': summary['http_req_duration'],
                    'requests': summary['http_reqs'], 'failures': summary['app_failures'],
                    'http_failures': summary['http_req_failed']}
            result['trials'].append(entry)
            destination.write_text(json.dumps(result, indent=2))
        result['status'] = 'passed'
    except Exception as exc:
        result['status'] = 'failed'
        result['error'] = str(exc)
        raise
    finally:
        destination.write_text(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
