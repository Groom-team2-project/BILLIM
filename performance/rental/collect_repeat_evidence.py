"""반복 측정 컨테이너의 종료 상태·데드락 로그만 읽기 전용 수집."""

import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]


def main():
    for batch in (ROOT/'docs/performance/rental/raw/repeated').iterdir():
        manifest=json.loads((batch/'manifest.json').read_text())
        if 'finishedAt' not in manifest:
            continue
        statuses=[]
        for row in manifest['rounds']:
            backend=row['project']+'-backend-1'
            result=subprocess.run(['docker','logs',backend],capture_output=True,text=True)
            lines=(result.stdout+'\n'+result.stderr).splitlines()
            selected=[line[:1000] for line in lines if any(word in line for word in ('ErrorCode: 1213','Deadlock found when trying to get lock','CannotAcquireLockException:'))]
            destination=batch/f"run-{row['repeat']}"
            (destination/'deadlock-evidence.txt').write_text('\n'.join(selected)+'\n')
            for service in ('backend','mysql'):
                container=row['project']+'-'+service+'-1'
                state=subprocess.check_output(['docker','inspect','--format','{{.State.Running}}',container],text=True).strip()
                statuses.append({'container':container,'running':state=='true'})
        (batch/'container-states.json').write_text(json.dumps(statuses,indent=2)+'\n')
        if any(row['running'] for row in statuses):
            raise SystemExit('종료되지 않은 측정 컨테이너 존재')
    print('종료 상태·데드락 근거 수집 완료')


if __name__=='__main__':
    main()
