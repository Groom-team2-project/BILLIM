"""비교 대상 DB 버전에 맞춘 성능 시드 소속 컬럼 확인."""

import subprocess


def membership_slot_fields(command):
    result = subprocess.run(command, input=(
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema=DATABASE() AND table_name='community_memberships' "
        "AND column_name='active_slot';\n"
    ), text=True, capture_output=True, check=True)
    lines = result.stdout.strip().splitlines()
    if lines and lines[-1] == "1":
        return ",active_slot", ",1"
    if lines and lines[-1] == "0":
        return "", ""
    raise RuntimeError("성능 DB의 소속 스키마를 확인할 수 없습니다.")
