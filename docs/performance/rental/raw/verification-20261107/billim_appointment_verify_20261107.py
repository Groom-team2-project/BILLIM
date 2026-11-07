import os,sys,json,hashlib,shutil
from pathlib import Path
ROOT=Path('/Users/sobean/IdeaProjects/BILLIM')
os.environ['RENTAL_LIST_PROJECT']='billim-rental-verify-20261107'
os.environ['RENTAL_LIST_LOCAL']='/tmp/billim-appointment-verify-20261107'
os.environ['RENTAL_FIXTURE_DIR']=os.environ['RENTAL_LIST_LOCAL']
sys.path.insert(0,str(ROOT/'docs/performance/tools/rental'))
import compare_list as c
folder=Path('/tmp/billim-appointment-verify-20261107')
folder.mkdir(exist_ok=True,mode=0o700)
source=(ROOT/'docs/performance/tools/rental/run_appointment.py').read_text()
source=source.replace('981000 AND 981020','910000 AND 910049').replace('!= 21:', '!= 42:').replace('981001','910001').replace('981002','910030')
source=source.replace('980000','910000')
scope={'__name__':'verification'}
exec(compile(source,'run_appointment.py','exec'),scope)
scope.update(sql=c.sql,LOCAL=folder,HERE=folder)
script=(ROOT/'docs/performance/scripts/rental-appointment.js').read_text()
script=script.replace('vus: 1, iterations: 1, batch: 2, batchPerHost: 2,','scenarios: { race: { executor: "per-vu-iterations", vus: 10, iterations: 1 } }, batch: 2, batchPerHost: 2,')
script=script.replace('fixture.cases.forEach((entry, index) => {','fixture.cases.filter((_, i) => i % 10 === __VU - 1).forEach((entry, index) => {')
(folder/'appointment-race.js').write_text(script)
scope['main']()
out=ROOT/'docs/performance/rental/raw/verification-20261107'
for name in ['appointment-race.json','appointment-verification.json','appointment-race.log','appointment-race.js']:
    shutil.copyfile(folder/name,out/name)
