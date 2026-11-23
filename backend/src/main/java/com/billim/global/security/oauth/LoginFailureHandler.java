package com.billim.global.security.oauth;

import com.billim.global.exception.ErrorCode;
import com.billim.global.security.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 카카오 인증 실패 처리. 사용자 취소와 state 검증 실패를 구분 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    /** 제공자가 사용자 거부에 사용하는 표준 오류 코드 */
    private static final String ACCESS_DENIED = "access_denied";

    private final SecurityErrorResponseWriter errorResponseWriter;
    private final String loginCancelUrl;

    public LoginFailureHandler(SecurityErrorResponseWriter errorResponseWriter,
                               @Value("${billim.auth.login-cancel-url}") String loginCancelUrl) {
        this.errorResponseWriter = errorResponseWriter;
        this.loginCancelUrl = loginCancelUrl;
    }

    /**
     * 사용자 취소는 로그인 화면으로 복귀. 그 외(state 불일치·재사용·만료)는 400.
     * state 실패를 화면으로 돌려보내면 공격 시도와 정상 취소가 구분되지 않음.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (ACCESS_DENIED.equals(errorCodeOf(exception))) {
            response.sendRedirect(loginCancelUrl);
            return;
        }
        errorResponseWriter.write(response, ErrorCode.INVALID_REQUEST);
    }

    private String errorCodeOf(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : null;
    }
}
