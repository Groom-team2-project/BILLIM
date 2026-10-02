/** 물건 API 응답 → 화면 모델 변환, 검색 조건 → API 쿼리 변환 테스트 (Node 내장 test runner) */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  formatAgo,
  formatDistance,
  formatMonthDay,
  parseIsoDate,
  toCardItem,
  toCategoryId,
  toMapPins,
  toSearchQuery,
} from "../src/utils/itemView.ts";
import { toIsoDate } from "../src/utils/registerForm.ts";

const summary = {
  id: "7",
  title: "충전식 전동드릴",
  category: { code: "TOOL" },
  owner: { displayName: "정우" },
  place: { name: "정문 경비실 옆 벤치" },
  thumbnailUrl: "/api/v1/media/5/content",
  distanceMeters: 1250,
  createdAt: "2026-10-02T00:00:00Z",
};

test("검색 조건 전달: 키워드·카테고리·기간·정렬·페이지를 API 명세 이름으로 보낸다", () => {
  const q = toSearchQuery(
    {
      keyword: "  드릴 ",
      categoryId: "1",
      period: [new Date(2026, 9, 5), new Date(2026, 9, 7)],
      sort: "near",
      page: 2,
      size: 20,
    },
    toIsoDate,
  );
  assert.deepEqual(q, {
    keyword: "드릴",
    categoryId: "1",
    startDate: "2026-10-05",
    endDate: "2026-10-07",
    sort: "NEAREST",
    page: 2,
    size: 20,
  });
});

test("검색 조건 전달: 빈 키워드·미선택 카테고리·기간 없음은 보내지 않고 기본 정렬은 LATEST", () => {
  const q = toSearchQuery({ keyword: "   ", categoryId: null, period: null, sort: "new", page: 0, size: 20 }, toIsoDate);
  assert.deepEqual(q, { sort: "LATEST", page: 0, size: 20 });
  assert.equal("startDate" in q, false, "기간은 시작·종료를 함께 보내거나 둘 다 생략");
});

test("목록 카드: 서버 값으로 제목·카테고리·장소·거리·대표 사진을 채운다", () => {
  const card = toCardItem(summary, new Date("2026-10-02T00:30:00Z"));
  assert.equal(card.id, "7");
  assert.equal(card.cat, "tool");
  assert.equal(card.owner, "정우");
  assert.equal(card.place, "정문 경비실 옆 벤치");
  assert.equal(card.dist, "1.3km");
  assert.equal(card.thumbnailUrl, "/api/v1/media/5/content");
  assert.equal(card.ago, "30분 전");
  assert.equal(card.avail, "ok");
  assert.equal(card.trust, undefined, "신뢰 지표(C_008) 연동 전에는 표시하지 않는다");
});

test("목록 카드: 기간 검색에서 불가로 온 물건은 대여 불가로 표시한다", () => {
  assert.equal(toCardItem({ ...summary, availableForRange: false }).avail, "none");
  assert.equal(toCardItem({ ...summary, availableForRange: true }).avail, "ok");
});

test("카테고리 코드는 기존 아이콘 키로 바뀌고 모르는 코드는 기타", () => {
  assert.equal(toCategoryId("SPORT"), "sports");
  assert.equal(toCategoryId("OTHER"), "etc");
  assert.equal(toCategoryId("NEW_CODE"), "etc");
});

test("거리 표시: 1km 미만은 m, 이상은 km", () => {
  assert.equal(formatDistance(0), "0m");
  assert.equal(formatDistance(999), "999m");
  assert.equal(formatDistance(1000), "1.0km");
});

test("등록 시각 표시", () => {
  const now = new Date("2026-10-02T12:00:00Z");
  assert.equal(formatAgo("2026-10-02T11:59:40Z", now), "방금 전");
  assert.equal(formatAgo("2026-10-02T09:00:00Z", now), "3시간 전");
  assert.equal(formatAgo("2026-10-01T11:00:00Z", now), "어제");
  assert.equal(formatAgo("2026-09-27T12:00:00Z", now), "5일 전");
});

test("날짜: YYYY-MM-DD를 달력 날짜로 읽고 M/D(요일)로 표시", () => {
  const d = parseIsoDate("2026-10-05");
  assert.equal(d.getFullYear(), 2026);
  assert.equal(d.getMonth(), 9);
  assert.equal(d.getDate(), 5);
  assert.equal(formatMonthDay(d), "10/5(월)");
});

test("지도 핀: 같은 공용 장소 물건을 묶어 개수를 세고 좌표를 지도 영역 안에 둔다", () => {
  const at = (id: string, lat: number, lng: number) => ({ place: { id, name: `장소${id}`, latitude: lat, longitude: lng } });
  const pins = toMapPins([at("1", 37.5, 127.0), at("1", 37.5, 127.0), at("2", 37.6, 127.1)]);
  assert.equal(pins.length, 2);
  assert.equal(pins.find((p) => p.id === "1")?.n, 2);
  for (const p of pins) {
    assert.ok(p.x >= 0 && p.x <= 100 && p.y >= 0 && p.y <= 100);
  }
  const north = pins.find((p) => p.id === "2");
  const south = pins.find((p) => p.id === "1");
  assert.ok(north && south && north.y < south.y, "북쪽 장소가 위에 있다");
  assert.deepEqual(toMapPins([]), []);
});
