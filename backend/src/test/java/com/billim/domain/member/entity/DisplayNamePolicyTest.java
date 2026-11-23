package com.billim.domain.member.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 표시 이름 문자·사칭 규칙. 보이지 않는 이름과 운영자 사칭은 이웃 간 거래 신뢰를 직접 훼손.
 */
class DisplayNamePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "\u200B\u200B",          // 폭 없는 공백만 — 보이지 않는 이름
            "홍길\u200B동",           // 폭 없는 공백 삽입 — 같은 이름처럼 보이는 다른 이름
            "\u202E동길홍",           // 방향 뒤집기 — 표시 순서 조작
            "홍길\u0007동",           // 제어 문자
            "홍길\uE000동",           // 사용자 정의 영역
            "홍길\u2028동",           // 줄 구분
            "\u3164\u3164",          // 한글 채움 문자만 — 투명 이름
            "홍\u3164길동",           // 한글 채움 문자 끼우기
            "\uFE0F\uFE0F",          // 변형 선택자만 — 보이는 글자 없음
            "\u0301\u0301"           // 결합 부호만
    })
    @DisplayName("보이지 않는 문자·방향 제어·제어 문자가 섞인 이름은 거부한다")
    void rejectsForbiddenCharacters(String name) {
        Member member = Member.register("홍길동");

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> member.changeDisplayName(name))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"관리자", "빌림 관리자", "관 리 자", "운영팀입니다", "admin", "ADMIN", "ＡＤＭＩＮ", "a.d.m.i.n", "Billim공식", "공식 ADMIN", "빌림billim", "\u0430dmin", "b\u0456llim", "adm\u0456n"})
    @DisplayName("운영자로 오인될 이름은 정규화 후 거부한다")
    void rejectsReservedNames(String name) {
        Member member = Member.register("홍길동");

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> member.changeDisplayName(name))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    /** 영문 예약어는 시작 일치 — 포함 일치면 일반 단어까지 막힘 */
    @ParameterizedTest
    @ValueSource(strings = {"badminton", "홍길동", "빌려줄게요", "이웃 김씨 😀", "❤️캠핑러", "❤️", "my badminton"})
    @DisplayName("일반 이름과 이모지는 허용한다")
    void allowsOrdinaryNames(String name) {
        Member member = Member.register("홍길동");

        assertThatCode(() -> member.changeDisplayName(name)).doesNotThrowAnyException();
    }

    /** 관리자 전용 로그인 계정은 환경 변수 자격증명으로만 생성 — 사칭 규칙 대상 아님 */
    @Test
    @DisplayName("관리자 전용 계정은 관리자 이름으로 생성된다")
    void adminAccountKeepsReservedName() {
        assertThat(Member.registerAdmin("관리자").getDisplayName()).isEqualTo("관리자");
    }

    @Test
    @DisplayName("금지 문자 제거는 보이는 글자를 남긴다")
    void stripsForbiddenCharacters() {
        assertThat(DisplayNamePolicy.stripForbidden("홍\u200B길\u202E동")).isEqualTo("홍길동");
    }
}
