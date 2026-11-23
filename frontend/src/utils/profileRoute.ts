/** 물건 작성자가 본인이면 내 프로필, 다른 회원이면 회원 ID 기반 이웃 프로필 */
export function ownerProfilePath(ownerId: string, own: boolean): string {
  return own ? "/profile" : `/members/${encodeURIComponent(ownerId)}`;
}
