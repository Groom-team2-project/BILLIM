import assert from "node:assert/strict";
import test from "node:test";
import { ownerProfilePath } from "../src/utils/profileRoute.ts";

test("내 물건의 작성자 프로필은 내 프로필로 이동한다", () => {
  assert.equal(ownerProfilePath("1", true), "/profile");
});

test("다른 사람 물건의 작성자 프로필은 회원 ID 기반 이웃 프로필로 이동한다", () => {
  assert.equal(ownerProfilePath("2", false), "/neighbors/2");
});

test("이웃 회원 ID를 URL 경로에 안전하게 넣는다", () => {
  assert.equal(ownerProfilePath("member/id", false), "/neighbors/member%2Fid");
});
