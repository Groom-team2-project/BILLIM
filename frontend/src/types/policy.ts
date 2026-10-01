/** 정책·안내 문서 타입 */
/** 정책·안내 본문 — 프로토타입 POLICIES 원문. 문구 수정은 팀 합의 후 (docs/policy와 동기화) */
export type Policy = { title: string; md: string };

/** 허브 화면 — 소개·고객센터에서 정책으로 연결 */
export type PolicyHub = { title: string; body: string; links: [string, string][] };
