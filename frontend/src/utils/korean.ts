/** 한국어 조사 — 받침 유무로 선택 (목·목록 문구 생성용) */
function hasFinalConsonant(word: string): boolean {
  const last = word.trim().at(-1);
  if (!last) return false;
  const code = last.charCodeAt(0);
  /* 한글 음절: 받침 인덱스가 0이 아니면 받침 있음 */
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 !== 0;
  /* 영문·숫자: 발음 기준 대략치 (L, M, N, R, 0·1·3·6·7·8 등) */
  return /[lmnr1368]$/i.test(last);
}

/** 을/를 */
export function objectParticle(word: string): string {
  return hasFinalConsonant(word) ? "을" : "를";
}
