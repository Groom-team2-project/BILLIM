/** API 공통 호출 — 쿠키 세션 + CSRF + Idempotency-Key (API 명세 0장) */

const BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

export type FieldError = { field: string; reason: string };

/** 서버 오류 응답(ApiError) 또는 네트워크 실패 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: FieldError[];
  readonly currentVersion?: number;

  constructor(status: number, code: string, message: string, extra?: { fieldErrors?: FieldError[]; currentVersion?: number }) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = extra?.fieldErrors;
    this.currentVersion = extra?.currentVersion;
  }

  get unauthenticated() {
    return this.status === 401;
  }
}

let csrfToken: string | null = null;

/** TODO(A): 인증 담당 API(A_001). 로그인 후에는 다시 받아야 하므로 로그인 흐름에서 resetCsrf 호출 */
async function loadCsrf(): Promise<string> {
  const res = await rawFetch("/auth/csrf", { method: "GET", headers: { "X-Requested-With": "XMLHttpRequest" } });
  if (!res.ok) throw await toError(res);
  const data = (await res.json()) as { csrfToken: string };
  csrfToken = data.csrfToken;
  return csrfToken;
}

export function resetCsrf() {
  csrfToken = null;
}

async function rawFetch(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${BASE}${path}`, { credentials: "include", ...init });
  } catch {
    throw new ApiError(0, "NETWORK", "네트워크 연결을 확인한 뒤 다시 시도해 주세요.");
  }
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { code?: string; message?: string; fieldErrors?: FieldError[]; currentVersion?: number };
    return new ApiError(res.status, body.code ?? "UNKNOWN", body.message ?? "요청을 처리하지 못했어요.", {
      fieldErrors: body.fieldErrors,
      currentVersion: body.currentVersion,
    });
  } catch {
    return new ApiError(res.status, "UNKNOWN", "요청을 처리하지 못했어요.");
  }
}

type Options = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, string | number | undefined | null>;
  json?: unknown;
  form?: FormData;
  /** POST는 UUID 필수. 재전송(같은 요청)이면 같은 키를 넘긴다 */
  idempotencyKey?: string;
};

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const method = opts.method ?? "GET";
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(opts.query ?? {})) {
    if (v !== undefined && v !== null && v !== "") qs.set(k, String(v));
  }
  const url = qs.size ? `${path}?${qs}` : path;

  const send = async (retriedCsrf: boolean): Promise<T> => {
    // XHR 표시: 401 응답에 Basic 인증 요구 헤더가 붙지 않아 브라우저 인증창이 뜨지 않는다 (Spring Security 기본 동작)
    const headers: Record<string, string> = { "X-Requested-With": "XMLHttpRequest" };
    if (opts.json !== undefined) headers["Content-Type"] = "application/json";
    if (method === "POST") headers["Idempotency-Key"] = opts.idempotencyKey ?? newIdempotencyKey();
    if (method !== "GET") headers["X-CSRF-TOKEN"] = csrfToken ?? (await loadCsrf());

    const res = await rawFetch(url, {
      method,
      headers,
      body: opts.form ?? (opts.json !== undefined ? JSON.stringify(opts.json) : undefined),
    });
    if (res.ok) {
      return (res.status === 204 ? undefined : await res.json()) as T;
    }
    const err = await toError(res);
    if (err.code === "CSRF_INVALID" && !retriedCsrf) {
      csrfToken = null;
      return send(true);
    }
    throw err;
  };
  return send(false);
}
