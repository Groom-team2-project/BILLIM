package com.billim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
// OAuth2 client-id/secret은 실제 환경변수 없이도 컨텍스트가 뜨도록 테스트 값을 주입
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class BillimApplicationTests {

    @Test
    void contextLoads() {
    }

}
