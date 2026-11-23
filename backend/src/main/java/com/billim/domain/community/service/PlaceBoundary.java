package com.billim.domain.community.service;

/**
 * 공용 장소 후보를 동네 경계 안으로 좁히는 기준.
 *
 * <p>지도 제공자는 반경 안의 공공기관을 가까운 순으로 주지만 경계를 모른다. 역삼1동 중심
 * 5 km에는 서초구 주민센터도 들어온다. 후보의 주소 문자열로 걸러내면 추가 호출이 없다.
 *
 * <p>후보 주소는 법정동 기준("서울 강남구 역삼동 829-20")이고 동네 이름은 행정동("역삼1동")이라
 * 그대로 비교할 수 없다. 그래서 두 단계로 본다.
 * <ul>
 *   <li>{@code sigungu} — 다르면 경계 밖이므로 제외한다
 *   <li>{@code districtStem} — 같으면 같은 동네로 보고 먼저 쓴다. 다르면 같은 시군구 안의
 *       다른 동이라 후순위로 둔다. 행정동과 법정동 이름이 아예 다른 동네에서도
 *       후보가 0건이 되지 않게 하려는 장치다
 * </ul>
 *
 * @param sigungu      시군구 이름. 예: 강남구, 평창군
 * @param districtStem 동네 이름의 어간. 예: 역삼1동 → 역삼, 이도이동 → 이도이, 대관령면 → 대관령면
 */
public record PlaceBoundary(String sigungu, String districtStem) {

    /**
     * 동네의 공개 주소와 이름에서 기준을 뽑는다.
     *
     * @param displayAddress 예: 서울특별시 강남구 역삼1동
     * @param name           예: 역삼1동
     */
    public static PlaceBoundary of(String displayAddress, String name) {
        return new PlaceBoundary(sigunguOf(displayAddress), stemOf(name));
    }

    /**
     * 공개 주소의 끝에서 두 번째 조각. 마지막 조각이 동네 이름이기 때문이다.
     *
     * <p>세종처럼 시군구가 없는 주소("세종특별자치시 한솔동")는 시 이름이 잡히고,
     * 후보 주소에도 그 이름이 들어가므로 비교가 성립한다.
     */
    private static String sigunguOf(String displayAddress) {
        if (displayAddress == null || displayAddress.isBlank()) {
            return "";
        }
        String[] parts = displayAddress.strip().split("\\s+");
        return parts.length < 2 ? "" : parts[parts.length - 2];
    }

    /**
     * 동네 이름에서 아라비아 숫자를 떼고, 끝의 '동'도 뗀다.
     *
     * <p>행정동 "역삼1동"과 법정동 "역삼동"이 어간 "역삼"에서 만난다. 한글 숫자는 떼지 않는다 —
     * "이도이동"에서 '이'를 떼면 "이도1동"과 구분할 수 없다. 읍·면은 이름이 그대로 주소에
     * 들어오므로 손대지 않는다.
     */
    private static String stemOf(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String stem = name.strip().replaceAll("[0-9]", "");
        if (stem.length() > 1 && stem.endsWith("동")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        return stem;
    }

    /** 경계 밖이면 쓰지 않는다 */
    public boolean containsAddress(String address) {
        return address != null && !sigungu.isEmpty() && address.contains(sigungu);
    }

    /** 같은 동네의 장소인지. 먼저 쓸 후보를 고르는 데 쓴다 */
    public boolean isSameDistrict(String address) {
        return address != null && !districtStem.isEmpty() && address.contains(districtStem);
    }
}
