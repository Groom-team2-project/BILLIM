import os,sys,secrets,hashlib,json
from pathlib import Path
root=Path('/Users/sobean/IdeaProjects/BILLIM')
os.environ['RENTAL_LIST_PROJECT']='billim-rental-verify-20261107'
sys.path.insert(0,str(root/'docs/performance/tools/rental'))
import compare_list as c
tokens={}
for role,member in [('borrower',910000),('owner',910001)]:
    token=secrets.token_urlsafe(32);csrf=secrets.token_urlsafe(32)
    digest=hashlib.sha256(token.encode()).hexdigest()
    c.sql(f"INSERT INTO auth_sessions(member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES({member},'{digest}','{csrf}',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 4 HOUR,UTC_TIMESTAMP(6)+INTERVAL 4 HOUR,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
    tokens[role]=token
path=Path('/tmp/billim-live-sessions-20261107.json')
path.touch(mode=0o600)
path.write_text(json.dumps(tokens))
print('화면 검증용 임시 세션 2개 준비 완료')
