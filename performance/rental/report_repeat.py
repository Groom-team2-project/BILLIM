"""3회 반복 원본으로 공통 양식 보고서 생성. 개선 전후 비교 제외."""

from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import statistics
import sys

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
DOCS=ROOT/'docs/performance'
SPECS=[
    ('rental-list','대여목록','list','baseline','GET /api/v1/rentals?role=BORROWER&page=0&size=20','10 VU·준비 30초→유지 3분·응답 후 1초 대기','회원 21·물건 1,000·대여 1,000, 조회 계정에 1,000건 집중','rental-list.js'),
    ('concurrent-approval','동시승인','approval','approval','POST /api/v1/rentals/{id}/approve','1 VU·물건당 HTTP 동시 10건×20개·별도 20개 물건 준비 실행','회원 21·전체 물건 1,040·대여 1,400건(선행 준비 포함), 대상 물건 20개·요청 200건, 같은 소유자·요청자 10명','rental-approval.js'),
    ('idempotency','승인재전송','idempotency','idempotency','POST /api/v1/rentals/{id}/approve','1 VU·동일 키 HTTP 동시 10건+완료 후 1건+본문 변경 1건×20개·동일 시나리오 준비 실행','회원 21·전체 물건 1,080·대여 1,800건(선행 준비 포함), 작업 20개·HTTP 240건, 실제 승인 기대 20건','rental-idempotency.js'),
    ('appointment-race','약속동시변경','appointment','appointment-race','POST /api/v1/appointments/{id}/accept, withdraw','1 VU·수락/철회 HTTP 동시 2건×20개·별도 20개 약속 준비 실행','회원 21·전체 물건 1,120·대여 1,840건(선행 준비 포함), 대상 승인 거래 20·슬롯 40·전달 제안 20, 같은 사용자 쌍','rental-appointment.js'),
    ('create-rental','대여생성','create','create-baseline','POST /api/v1/rentals','10 VU·사용자별 준비 30회(약 30초 이상)→유지 3분·응답 후 1초 대기','요청자 10·소유자 10, 새 물건 2,420개 준비, 요청마다 새 물건·키','rental-create.js'),
]


def load(path):
    return json.loads(path.read_text())


def summary(values,suffix=''):
    return f'{statistics.median(values):.2f}{suffix} / 범위 {min(values):.2f}~{max(values):.2f} / 표준편차 {statistics.pstdev(values):.2f}'


def main():
    all_batches=sorted((DOCS/'rental/raw/repeated').iterdir())
    batch=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else next(b for b in reversed(all_batches) if not load(b/'manifest.json').get('createOnly'))
    create_batch=Path(sys.argv[2]).resolve() if len(sys.argv)>2 else next((b for b in reversed(all_batches) if load(b/'manifest.json').get('createOnly')),None)
    manifest=load(batch/'manifest.json')
    if len(manifest['rounds'])!=3 or 'finishedAt' not in manifest:
        raise SystemExit('정상 종료한 3회 반복 원본 필요')
    date=datetime.fromisoformat(manifest['finishedAt']).astimezone(timezone(timedelta(hours=9))).strftime('%Y.%m.%d')
    index=[]
    for old,name,label,stem,api,load_desc,data,script in SPECS:
        source=create_batch if label=='create' else batch
        if source is None or 'finishedAt' not in load(source/'manifest.json'):
            raise SystemExit('생성 전용 초기화 반복 원본 3회 필요')
        source_manifest=load(source/'manifest.json')
        if source_manifest['commit']!=manifest['commit']:
            raise SystemExit('서버 코드 커밋이 다른 반복 자료의 혼합 집계 금지')
        date=datetime.fromisoformat(source_manifest['finishedAt']).astimezone(timezone(timedelta(hours=9))).strftime('%Y.%m.%d')
        triples=[(source/f'run-{i}'/label) for i in range(1,4)]
        metrics=[load(p/(stem+'.json'))['metrics'] for p in triples]
        observations=[load(p/(stem+'-observation.json')) for p in triples]
        rate=[m['http_reqs']['rate'] for m in metrics]
        p50=[m['http_req_duration']['med'] for m in metrics]
        p95=[m['http_req_duration']['p(95)'] for m in metrics]
        p99=[m['http_req_duration']['p(99)'] for m in metrics]
        sqls=[o['sqlPerRequest'] for o in observations]
        errors=[m['unexpected_responses']['value'] if 'unexpected_responses' in m else m['http_req_failed']['value'] for m in metrics]
        unstable=any(max(v)>2*min(v) for v in (p50,p95) if min(v)>0)
        conditions=[
            ('측정일 · 담당',date+' · 박소빈'),('구분','로컬 · 동일 코드 반복 측정, 개선 전후 비교 제외'),
            ('서버','macOS arm64 부하 생성기 + Docker MySQL 8.4·Java 21 백엔드 1대, 메모리 상한 1GiB, CPU 전용 할당 없음'),
            ('코드',f"`{manifest['commit']}` · 측정 도구는 이번 작업 변경 포함"),('데이터',data),('대상 API',f'`{api}`'),
            ('부하',load_desc),('반복 조건','서로 다른 새 전용 DB 3개, 같은 데이터 생성 코드·고정 키 순서·시나리오 순서. 동일 KST 상대 날짜 사용, 세션 토큰은 매회 신규'),
            ('성공 기준','응답·대상·상태 검증 100%, 예상 밖 오류 0%, DB 정합성 일치. p95 수치는 관측값이며 시간 목표 미설정'),
            ('SQL 정의','앱 DB 계정 SELECT/INSERT/UPDATE/DELETE 실행 횟수 차이 ÷ 해당 HTTP 요청 수. 계측·준비 SQL은 root 계정으로 제외'),
            ('서버 지표','관리 포트 localhost:9090의 /actuator/prometheus를 컨테이너 내부에서 약 3초 간격 수집'),
        ]
        text=f'# 박소빈-{name} 측정\n\n> 양식: [성능 측정 양식](성능%20측정%20양식.md)\n> 개선 전·후 비교는 사용자 요청에 따라 제외. 1·2·3회는 개선 차수가 아닌 같은 코드 반복.\n\n## 1. 조건\n\n| 항목 | 값 |\n|---|---|\n'
        text+=''.join(f'| {k} | {v} |\n' for k,v in conditions)
        text+='\n## 2. 결과\n\n### 로컬\n\n| 지표 | 1회 | 2회 | 3회 | 중위값·편차 |\n|---|---:|---:|---:|---|\n'
        rows=[('요청 수',[m['http_reqs']['count'] for m in metrics],''),('처리량 (req/s)',rate,''),('p50 (ms)',p50,'ms'),('p95 (ms)',p95,'ms'),('p99 (ms)',p99,'ms'),('예상 밖 오류율 (%)',[e*100 for e in errors],'%'),('요청당 SQL',sqls,'회')]
        for title,values,suffix in rows:
            if unstable and title in ('처리량 (req/s)','p50 (ms)','p95 (ms)','p99 (ms)'):
                text+=f'| {title} | — | — | — | 시간 편차 2배 초과, 집계 유보 |\n'
            else:
                text+=f'| {title} | '+' | '.join(f'{v:.2f}' for v in values)+' | '+summary(values,suffix)+' |\n'
        for operation in ('select','insert','update','delete'):
            values=[o['sqlDelta'].get(operation,0)/o['requests'] for o in observations]
            text+=f'| 요청당 {operation.upper()} | '+' | '.join(f'{v:.2f}' for v in values)+' | '+summary(values,'회')+' |\n'
        text+='\n- 편차는 3회 구간 지표의 모집단 표준편차와 최솟값~최댓값으로 표기. p95 중위값은 원본 요청 전체를 합친 p95와 다름.\n'
        text+='- 응답 시간은 해당 구간 전체 HTTP 응답 기준. 승인 성공·예상 충돌·서버 오류가 섞인 경우 각각의 성공 지연과 동일하게 해석하지 않음.\n'
        text+='- p50 또는 p95의 회차 최댓값/최솟값이 2배를 넘으면 공통 양식의 큰 편차 원칙에 따라 시간 기반 집계를 비움. 개별 원본은 유지.\n'
        text+='- SQL은 드라이버·인증·권한 확인을 포함한 앱 계정의 DB 실행 횟수. 실패 시 롤백된 DML 실행도 포함하며 커밋 행 수와 다름.\n'
        text+='- 시간 구간 양끝의 계정 누적 카운터 차이를 사용. SQL 준비·후검증은 별도 root 계정으로 실행, outbox 소비 비활성·요청 만료 주기 24시간·인증 정리 cron 비활성으로 주기 작업 혼입 억제.\n'
        if label=='approval':
            text+='\n동시 요청 분포: 각 회차 승인 '+', '.join(str(int(m.get('approved_responses',{}).get('count',0))) for m in metrics)+'건 / 날짜 충돌 '+', '.join(str(int(m.get('date_conflict_responses',{}).get('count',0))) for m in metrics)+'건. 기대값은 물건별 승인 1건·409 9건.\n'
        if label=='appointment':
            text+='\n동시 요청 분포: 각 회차 수락 성공 '+', '.join(str(int(m.get('accept_successes',{}).get('count',0))) for m in metrics)+'건 / 철회 성공 '+', '.join(str(int(m.get('withdraw_successes',{}).get('count',0))) for m in metrics)+'건. 약속별 성공 1건·VERSION_CONFLICT 1건 기대.\n'
        if label=='idempotency':
            text+='\n각 회차 같은 요청 동시 10건·완료 후 1건의 성공 응답 값 일치와 본문 변경 409 거부를 검증. 실제 승인 기대값은 20건.\n'
        verification_name={'approval':'approval-verification.json','idempotency':'idempotency-verification.json','appointment':'appointment-verification.json','create':'create-verification.json'}.get(label)
        if verification_name:
            results=[load(p/verification_name) for p in triples]
            text+='\nDB 후검증: '+', '.join(f"{i}회 {'통과' if r.get('databaseChecksPassed') else '실패'}" for i,r in enumerate(results,1))+'.\n'
        if label=='create':
            text+='\n#### 생성 재전송 별도 결과\n\n| 회차 | 요청 | 201 | 예상 밖 404 | 정상 키 재사용 409 | 응답 검증 | DB 검증 |\n|---|---:|---:|---:|---:|---|---|\n'
            for i,p in enumerate(triples,1):
                r=load(p/'create-replay.json')['metrics'];v=load(p/'create-verification.json')
                text+=f"| {i} | {int(r['http_reqs']['count'])} | {int(r.get('create_replay_successes',{}).get('count',0))} | {int(r.get('create_replay_not_found',{}).get('count',0))} | {int(r.get('create_changed_body_conflicts',{}).get('count',0))} | {'통과' if r['checks']['fails']==0 else '실패'} | {'통과' if v['databaseChecksPassed'] else '실패'} |\n"
            text+='\n생성 준비 구간의 실패율: '+', '.join(f"{i}회 {100*load(p/'create-warmup.json')['metrics']['http_req_failed']['value']:.2f}%" for i,p in enumerate(triples,1))+'. 준비 실패도 원본 유지.\n'
            initial=[load(p/'create-verification.json').get('beforeBaselineState') for p in triples]
            if any(state!=[21,3420,1000,1000,0,0] for state in initial):
                raise SystemExit('생성 본 측정 초기 건수 불일치')
            text+='- 준비 구간은 VU별 30회 요청으로 고정. 준비용 거래·이력·이벤트·멱등 기록을 본 구간 전에 정리. 세 회차 모두 본 측정 직전 회원 21·물건 3,420·대여 1,000·이력 1,000·이벤트 0·멱등 기록 0건 확인. 미정리한 선행 실험은 이번 집계에서 제외.\n'
        sample_counts=[o['actuatorSamples'] for o in observations]
        text+='\nActuator 표본 수: '+', '.join(f'{i}회 {v}개' for i,v in enumerate(sample_counts,1))+'. 구간별 원문 첫·마지막 표본 및 CPU·힙·연결 지표 시계열은 원본 관측 JSON에 보관. 짧은 경쟁 시험의 표본은 순간값으로만 해석.\n'
        text+='\n## 3. 해석과 한계\n\n'
        text+='- 현재 코드의 반복 관측 및 정합성 결과. 코드 최적화·개선 효과 판정은 수행하지 않음.\n'
        text+='- 예상 409는 업무 충돌이므로 예상 밖 오류와 분리. 기본 http_req_failed는 예상 409도 포함.\n'
        text+='- 부하 도구·서버·DB는 같은 PC, 다른 컨테이너와 자원 공유. 측정 중 추가 빌드·기능 테스트는 수행하지 않음. 운영 환경으로 일반화 불가.\n'
        text+='- 짧은 경쟁·재전송 시험의 요청률은 최대 처리량이 아님. VU 수와 HTTP batch 동시 연결 수는 구분.\n'
        text+='- 주기 작업을 억제한 전용 환경이므로 실제 주기 작업이 동작하는 운영 상황과 차이. 인증·API·DB 구조는 변경하지 않음.\n'
        if label=='create':
            text+='- 데드락·생성 재전송 404는 수정하지 않음. 신규 생성 오류율과 재전송 실패를 별도 평가하며 전체 성공으로 판정하지 않음.\n'
        text+='\n## 4. 재현\n\n```bash\ncd backend\n./gradlew bootJar\ncd ..\npython3 performance/rental/repeat.py\npython3 performance/rental/report_repeat.py\n```\n\n'
        text+=f'- k6 스크립트: [scripts/{script}](scripts/{script}). 준비 데이터·임시 세션은 실행 도구가 생성·폐기.\n'
        for i,p in enumerate(triples,1):
            rel=p.relative_to(DOCS).as_posix()
            text+=f'- {i}회 원본: [k6]({rel}/{stem}.json), [SQL·서버 계측]({rel}/{stem}-observation.json).\n'
        if label=='create':
            for i,p in enumerate(triples,1):
                rel=p.parent.relative_to(DOCS).as_posix()
                text+=f'- {i}회 서버 오류 근거: [데드락 로그]({rel}/deadlock-evidence.txt).\n'
        text+='- 예비 1회 측정은 반복 집계에 합산하지 않음. 기존 예비 문서·raw 파일 보존.\n'
        canonical=DOCS/f'박소빈-{name}.md'
        canonical.write_text(text)
        old_path=DOCS/'rental'/f'{old}.md'
        preliminary=old_path.with_name('preliminary-'+old_path.name)
        if not preliminary.exists():
            preliminary.write_text('> 예비 측정 기록. 공통 양식의 3회 반복 통계와 구분.\n\n'+old_path.read_text())
        old_path.write_text(f'# {name} 측정 문서\n\n공통 양식 본문: [박소빈-{name}](../박소빈-{name}.md).\n\n기존 기록: [예비 측정](preliminary-{old}.md).\n')
        index.append({'name':name,'document':canonical.name,'unstableTimeMetrics':unstable,'sqlMedian':statistics.median(sqls)})
    (batch/'report-index.json').write_text(json.dumps(index,ensure_ascii=False,indent=2)+'\n')
    navigation='# 대여 도메인 성능 측정 문서\n\n공통 양식에 따른 동일 코드 3회 반복 보고서. 개선 전·후 비교 제외.\n\n'
    for entry in index:
        navigation+=f"- [{entry['name']}](../{entry['document']})\n"
    navigation+='\n- `preliminary-*.md`: 이전 예비 측정 기록. 정식 반복 통계에 합산하지 않음.\n- `raw/repeated/`: 회차별 k6·SQL·Actuator 원본과 DB 검증.\n- 기존 문서 파일명은 새 보고서로 연결하는 호환 링크로 유지.\n'
    (DOCS/'rental/README.md').write_text(navigation)
    print('공통 양식 보고서 5개 생성 완료. 개선 전후 비교 제외.')


if __name__=='__main__':
    main()
