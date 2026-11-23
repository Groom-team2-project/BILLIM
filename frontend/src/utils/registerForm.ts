/** 물건 등록 폼의 순수 로직 — 외부 의존 없음 (tests/에서 node로 직접 검증) */

export const MAX_PHOTOS = 5;
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024; // 10MiB
export const MAX_TITLE = 100;
export const MAX_DESCRIPTION = 3000;
export const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

/** 서버 카테고리 코드 → 아이콘 이름 (기존 CATEGORIES 아이콘과 동일) */
const CATEGORY_ICONS: Record<string, string> = {
  TOOL: "tool",
  CAMP: "tent",
  TRAVEL: "luggage",
  BABY: "baby",
  MUSIC: "music",
  SPORT: "sports",
  KITCHEN: "kitchen",
  CLEAN: "broom",
  LIFE: "cup",
  OTHER: "grid",
};

export function categoryIcon(code: string): string {
  return CATEGORY_ICONS[code] ?? "grid";
}

/** 목록에서 from 위치의 항목을 to 위치로 옮긴 새 배열. 범위를 벗어나면 그대로 */
export function movePhoto<T>(list: readonly T[], from: number, to: number): T[] {
  const next = [...list];
  if (from === to || from < 0 || to < 0 || from >= next.length || to >= next.length) return next;
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** 파일 선택 단계의 빠른 검사. 최종 검증은 서버가 한다. 문제 없으면 null */
export function checkPhotoFile(file: { type: string; size: number }): string | null {
  if (file.size === 0) return "빈 파일이에요.";
  if (file.size > MAX_PHOTO_BYTES) return "사진은 10MB 이하만 올릴 수 있어요.";
  if (!ALLOWED_PHOTO_TYPES.includes(file.type)) return "JPG, PNG, WebP 사진만 올릴 수 있어요.";
  return null;
}

/** 로컬 달력 날짜 → YYYY-MM-DD */
export function toIsoDate(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${mm}-${dd}`;
}

export type RegisterPhoto = { mediaId?: string; status: "uploading" | "done" | "error" };

export type RegisterDraft = {
  title: string;
  description: string;
  categoryId: string | null;
  placeId: string | null;
  range: [Date, Date] | null;
  photos: RegisterPhoto[];
};

export type CreateItemBody = {
  title: string;
  description?: string;
  categoryId: string;
  placeId: string;
  availableStartDate: string;
  availableEndDate: string;
  imageIds: string[];
};

/** 첫 오류 메시지. 문제 없으면 null */
export function validateDraft(d: RegisterDraft): string | null {
  const title = d.title.trim();
  if (!title) return "제목을 입력해 주세요.";
  if (title.length > MAX_TITLE) return "제목은 100자 이하로 입력해 주세요.";
  if (d.description.length > MAX_DESCRIPTION) return "설명은 3000자 이하로 입력해 주세요.";
  if (!d.categoryId) return "카테고리를 선택해 주세요.";
  if (!d.placeId) return "거래 장소를 선택해 주세요.";
  if (!d.range) return "대여 가능 기간을 선택해 주세요.";
  if (d.photos.length < 1) return "사진을 1장 이상 등록해 주세요.";
  if (d.photos.length > MAX_PHOTOS) return "사진은 최대 5장까지 등록할 수 있어요.";
  if (d.photos.some((p) => p.status === "uploading")) return "사진을 올리는 중이에요. 잠시만 기다려 주세요.";
  if (d.photos.some((p) => p.status !== "done" || !p.mediaId)) return "올리지 못한 사진이 있어요. 지우고 다시 올려 주세요.";
  return null;
}

/** 검증을 통과한 폼 → 요청 본문. imageIds 순서가 곧 표시 순서이고 첫 번째가 대표 사진 */
export function toCreateBody(d: RegisterDraft): CreateItemBody {
  const description = d.description.trim();
  return {
    title: d.title.trim(),
    ...(description ? { description } : {}),
    categoryId: d.categoryId!,
    placeId: d.placeId!,
    availableStartDate: toIsoDate(d.range![0]),
    availableEndDate: toIsoDate(d.range![1]),
    imageIds: d.photos.map((p) => p.mediaId!),
  };
}
