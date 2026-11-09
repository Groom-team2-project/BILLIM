import sys,os,json
from pathlib import Path
root=Path('/Users/sobean/IdeaProjects/BILLIM')
os.environ.update(RENTAL_LIST_PROJECT='billim-rental-policy-20261107',RENTAL_LIST_LOCAL='/tmp/billim-rental-policy-20261107',RENTAL_LIST_RAW=str(root/'docs/performance/rental/raw/policy-compare-20261107'))
sys.path.insert(0,str(root/'docs/performance/tools/rental'))
import compare_list as c
if sys.argv[1]=='prepare':
 c.prepare()
 f=json.loads(c.FIXTURE.read_text());f['cases']={'owner-requested':f['cases']['owner-requested']};c.FIXTURE.write_text(json.dumps(f));c.FIXTURE.chmod(0o600)
elif sys.argv[1]=='revoke':
 c.sql("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE member_id IN (910000,910001) AND revoked_at IS NULL;");c.FIXTURE.unlink(missing_ok=True)
else:c.measure(sys.argv[1])
