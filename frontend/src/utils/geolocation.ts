/**
 * 현재 위치 한 번 읽기 (동네 찾기·인증용).
 * 좌표는 서버 요청에만 쓰고 저장하지 않는다. (API 명세 E_011, 정책 및 상태 5 위치 인증)
 */

export type Coords = { latitude: number; longitude: number; accuracyMeters: number };

/** 화면이 문구를 그대로 쓸 수 있게 사유를 구분한다 */
export type GeoFailure = "denied" | "unavailable" | "timeout" | "unsupported";

export class GeoError extends Error {
  readonly reason: GeoFailure;

  constructor(reason: GeoFailure, message: string) {
    super(message);
    this.reason = reason;
  }
}

const MESSAGES: Record<GeoFailure, string> = {
  denied: "위치 권한이 필요해요. 브라우저 설정에서 허용한 뒤 다시 시도해 주세요.",
  unavailable: "위치를 확인할 수 없어요. 잠시 후 다시 시도해 주세요.",
  timeout: "위치 확인이 오래 걸려요. 실외에서 다시 시도해 주세요.",
  unsupported: "이 브라우저에서는 위치를 사용할 수 없어요. 동네 이름으로 검색해 주세요.",
};

/** 반경 판정에 쓰므로 정확도를 높게 요청한다. 실패 사유는 GeoError로 구분해 올린다 */
export function getCurrentCoords(timeoutMs = 10_000): Promise<Coords> {
  if (!navigator.geolocation) {
    return Promise.reject(new GeoError("unsupported", MESSAGES.unsupported));
  }

  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          // 브라우저가 정확도를 주지 않으면 서버 상한(100m)에 걸리지 않게 0으로 둔다
          accuracyMeters: position.coords.accuracy ?? 0,
        }),
      (error) => {
        const reason: GeoFailure =
          error.code === error.PERMISSION_DENIED
            ? "denied"
            : error.code === error.TIMEOUT
              ? "timeout"
              : "unavailable";
        reject(new GeoError(reason, MESSAGES[reason]));
      },
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 0 },
    );
  });
}
