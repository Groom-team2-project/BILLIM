/** CSS Modules 클래스 매퍼 — 모듈에 있는 이름은 해시로, 전역 유틸(t-*, only-* 등)은 그대로 통과 */
export function classes(styles: Record<string, string>) {
  return (names: string) =>
    names
      .split(/\s+/)
      .filter(Boolean)
      .map((n) => styles[n] ?? n)
      .join(" ");
}
