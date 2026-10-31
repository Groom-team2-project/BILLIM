"""Netlify 서명(x-nf-sign) 생성 — 서명 강제(REQUIRE_PROXY_SIGNATURE) 운영 서버를 직접 측정할 때 사용.

백엔드 ProxySignatureVerifier가 검증하는 형식: HS256 JWS, iss=netlify, exp(초).
비밀값은 운영 PROXY_SIGNATURE_SECRET과 같은 값 — 출력 토큰도 유효 기간 동안 비밀값과 같은 취급.

사용: PROXY_SIGNATURE_SECRET=... python3 docs/performance/tools/auth/nf_sign.py [유효 분, 기본 120]
      k6 run -e NF_SIGN=$(...) ... member-read-multi.js
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def sign(secret: str, minutes: int) -> str:
    header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    claims = b64(json.dumps({"iss": "netlify", "exp": int(time.time()) + minutes * 60},
                            separators=(",", ":")).encode())
    signing_input = f"{header}.{claims}"
    signature = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{b64(signature)}"


if __name__ == "__main__":
    secret = os.environ.get("PROXY_SIGNATURE_SECRET")
    if not secret:
        sys.exit("PROXY_SIGNATURE_SECRET 환경 변수 필요")
    print(sign(secret, int(sys.argv[1]) if len(sys.argv) > 1 else 120))
