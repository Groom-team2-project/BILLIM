import json,sys,collections
ev=json.load(open(sys.argv[1]))['recording']['events']
def fr(f): m=f['method']; return m['type']['name']+'.'+m['name']
keys={'SQL 문장 파싱(QueryInfo 생성)':'com/mysql/cj/QueryInfo.<init>','PreparedStatement 생성':'com/mysql/cj/jdbc/ConnectionImpl.prepareStatement',
 '결과 행 읽기':'com/mysql/cj/protocol/a/','Hibernate 결과 매핑':'org/hibernate/sql/results','Hibernate SQL 생성·계획':'org/hibernate/sql/ast',
 'Hibernate flush·dirty check':'org/hibernate/event/internal/DefaultFlush','Hikari 반납·검사':'com/zaxxer/hikari/pool/ProxyConnection','TLS 암호화':'sun/security/ssl'}
c=collections.Counter(); n=0; hib=collections.Counter()
for e in ev:
    st=e['values'].get('stackTrace')
    if not st or not st.get('frames'): continue
    n+=1; fs=[fr(f) for f in st['frames']]
    for k,p in keys.items():
        if any(f.startswith(p) for f in fs): c[k]+=1
    h=next((f for f in fs if f.startswith('org/hibernate/')),None)
    if h: hib['.'.join(h.split('/')[2:4])]+=1
print('samples',n)
for k,v in c.most_common(): print(f'{v/n*100:5.1f}% {k}')
print('== Hibernate 패키지(leaf 쪽)')
for k,v in hib.most_common(8): print(f'{v/n*100:5.1f}% {k}')
