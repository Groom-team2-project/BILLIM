/** 물건 등록 폼 순수 로직 테스트 — Node 내장 test runner (추가 라이브러리 없음) */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  type RegisterDraft,
  categoryIcon,
  checkPhotoFile,
  movePhoto,
  toCreateBody,
  toIsoDate,
  validateDraft,
} from "../src/utils/registerForm.ts";

const done = (id: string) => ({ mediaId: id, status: "done" as const });

function draft(patch: Partial<RegisterDraft> = {}): RegisterDraft {
  return {
    title: "충전식 전동드릴",
    description: "",
    categoryId: "1",
    placeId: "10",
    range: [new Date(2026, 9, 5), new Date(2026, 9, 10)],
    photos: [done("5")],
    ...patch,
  };
}

test("필수값: 제목·카테고리·장소·기간·사진이 없으면 안내 문구를 돌려준다", () => {
  assert.equal(validateDraft(draft({ title: "   " })), "제목을 입력해 주세요.");
  assert.equal(validateDraft(draft({ categoryId: null })), "카테고리를 선택해 주세요.");
  assert.equal(validateDraft(draft({ placeId: null })), "거래 장소를 선택해 주세요.");
  assert.equal(validateDraft(draft({ range: null })), "대여 가능 기간을 선택해 주세요.");
  assert.equal(validateDraft(draft({ photos: [] })), "사진을 1장 이상 등록해 주세요.");
  assert.equal(validateDraft(draft()), null);
});

test("길이 제한: 제목 100자, 설명 3000자", () => {
  assert.equal(validateDraft(draft({ title: "가".repeat(100) })), null);
  assert.equal(validateDraft(draft({ title: "가".repeat(101) })), "제목은 100자 이하로 입력해 주세요.");
  assert.equal(validateDraft(draft({ description: "a".repeat(3001) })), "설명은 3000자 이하로 입력해 주세요.");
});

test("사진: 최대 5장, 올리는 중이거나 실패한 사진이 있으면 등록할 수 없다", () => {
  const six = ["1", "2", "3", "4", "5", "6"].map(done);
  assert.equal(validateDraft(draft({ photos: six })), "사진은 최대 5장까지 등록할 수 있어요.");
  assert.match(validateDraft(draft({ photos: [done("1"), { status: "uploading" }] })) ?? "", /올리는 중/);
  assert.match(validateDraft(draft({ photos: [done("1"), { status: "error" }] })) ?? "", /올리지 못한/);
});

test("사진 순서 변경: 5번째 사진을 맨 앞으로 옮기면 첫 번째(대표)가 된다", () => {
  const ids = ["1", "2", "3", "4", "5"];
  assert.deepEqual(movePhoto(ids, 4, 0), ["5", "1", "2", "3", "4"]);
  assert.deepEqual(movePhoto(ids, 0, 2), ["2", "3", "1", "4", "5"]);
  assert.deepEqual(movePhoto(ids, 1, 9), ids, "범위를 벗어나면 그대로");
  assert.deepEqual(ids, ["1", "2", "3", "4", "5"], "원본 배열은 바꾸지 않는다");
});

test("요청 본문: imageIds는 화면 순서 그대로이고 첫 번째가 대표 사진", () => {
  const photos = movePhoto([done("1"), done("2"), done("5")], 2, 0);
  const body = toCreateBody(draft({ photos, description: "  " }));
  assert.deepEqual(body.imageIds, ["5", "1", "2"]);
  assert.equal("description" in body, false, "빈 설명은 보내지 않는다");
  assert.equal(body.availableStartDate, "2026-10-05");
  assert.equal(body.availableEndDate, "2026-10-10");
  assert.equal(body.title, "충전식 전동드릴");
});

test("사진 추가·삭제: 사진 목록을 바꾸면 요청 본문의 imageIds도 따라간다", () => {
  const added = [...[done("1")], done("2")];
  assert.deepEqual(toCreateBody(draft({ photos: added })).imageIds, ["1", "2"]);
  const removed = added.filter((p) => p.mediaId !== "1");
  assert.deepEqual(toCreateBody(draft({ photos: removed })).imageIds, ["2"]);
});

test("파일 선택 검사: 빈 파일·10MiB 초과·GIF·TXT는 거부하고 JPEG·PNG·WebP는 허용한다", () => {
  assert.equal(checkPhotoFile({ type: "image/jpeg", size: 0 }), "빈 파일이에요.");
  assert.match(checkPhotoFile({ type: "image/jpeg", size: 10 * 1024 * 1024 + 1 }) ?? "", /10MB/);
  assert.match(checkPhotoFile({ type: "image/gif", size: 100 }) ?? "", /JPG, PNG, WebP/);
  assert.match(checkPhotoFile({ type: "text/plain", size: 100 }) ?? "", /JPG, PNG, WebP/);
  for (const type of ["image/jpeg", "image/png", "image/webp"]) {
    assert.equal(checkPhotoFile({ type, size: 10 * 1024 * 1024 }), null);
  }
});

test("카테고리 코드는 기존 아이콘으로 표시하고 모르는 코드는 기본 아이콘", () => {
  assert.equal(categoryIcon("TOOL"), "tool");
  assert.equal(categoryIcon("SPORT"), "sports");
  assert.equal(categoryIcon("UNKNOWN"), "grid");
});

test("날짜는 로컬 달력 날짜 그대로 YYYY-MM-DD", () => {
  assert.equal(toIsoDate(new Date(2026, 0, 3)), "2026-01-03");
  assert.equal(toIsoDate(new Date(2026, 11, 31, 23, 59)), "2026-12-31");
});
