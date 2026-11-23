import json,sys,collections
ev=json.load(open(sys.argv[1]))['recording']['events']
def fr(f): m=f['method']; return m['type']['name']+'.'+m['name']
# 스택 꼭대기(leaf)에서 내려가며 처음 만나는 범주에 귀속
rules=[('DB 통신 암호화(TLS)',('sun/security/ssl','com/sun/crypto','sun/security/provider')),
 ('MySQL 드라이버(SQL 파싱·결과 처리)',('com/mysql/',)),('Hikari 풀',('com/zaxxer/hikari',)),
 ('Hibernate·JPA',('org/hibernate/',)),('Jackson 직렬화',('com/fasterxml/jackson',)),
 ('로깅',('ch/qos/logback','org/slf4j')),('Micrometer 지표',('io/micrometer',)),
 ('Spring Security',('org/springframework/security',)),('Spring 트랜잭션·AOP',('org/springframework/transaction','org/springframework/aop','org/springframework/orm')),
 ('Spring MVC',('org/springframework/web',)),('Tomcat·HTTP',('org/apache/coyote','org/apache/tomcat','org/apache/catalina')),
 ('앱 코드',('com/billim/',))]
cat=collections.Counter(); incl=collections.Counter(); n=0; app=collections.Counter()
for e in ev:
    st=e['values'].get('stackTrace')
    if not st or not st.get('frames'): continue
    n+=1; frames=[fr(f) for f in st['frames']]
    c='기타(JDK·스레드 등)'
    for f in frames:
        h=[r for r,ps in rules if f.startswith(ps)]
        if h: c=h[0]; break
    cat[c]+=1
    seen=set()
    for f in frames:
        for r,ps in rules:
            if f.startswith(ps): seen.add(r)
    for r in seen: incl[r]+=1
    a=[f for f in frames if f.startswith('com/billim/')]
    if a: app[a[-1].replace('com/billim/','')]+=1   # 가장 바깥 앱 프레임 = 진입점
print('samples',n)
print('== 귀속(자기 시간)')
for k,v in cat.most_common(): print(f'{v/n*100:5.1f}% {k}')
print('== 포함(스택에 등장)')
for k,v in incl.most_common(): print(f'{v/n*100:5.1f}% {k}')
print('== 앱 진입점(가장 바깥 앱 프레임)')
for k,v in app.most_common(12): print(f'{v/n*100:5.1f}% {k}')
