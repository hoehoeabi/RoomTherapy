package com.roomtherapy.backend.global.security;

import com.roomtherapy.backend.global.util.CookieUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProfileIntegrationTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    @DisplayName("Local 프로파일 환경 테스트")
    class LocalProfileTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("로컬 환경에서는 테스트 로그인, 권한 전환, 데이터 시더 빈이 등록되고 secure 쿠키는 false여야 한다")
        void localProfile_beansAndCookieConfig() {
            assertThat(context.containsBean("testAuthController")).isTrue();
            assertThat(context.containsBean("userRoleController")).isTrue();
            assertThat(context.containsBean("dataInitializer")).isTrue();
            assertThat(CookieUtil.isSecure()).isFalse();
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.profiles.active=prod",
            "spring.datasource.url=jdbc:h2:mem:prodtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driver-class-name=org.h2.Driver"
    })
    @DisplayName("Production (운영) 프로파일 보안 격리 테스트")
    class ProdProfileTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("운영 환경에서는 테스트 백도어 및 권한 승격 빈이 등록되지 않아야 하고 secure 쿠키는 true여야 한다")
        void prodProfile_beansIsolatedAndSecureCookie() {
            assertThat(context.containsBean("testAuthController")).isFalse();
            assertThat(context.containsBean("userRoleController")).isFalse();
            assertThat(context.containsBean("dataInitializer")).isFalse();
            assertThat(CookieUtil.isSecure()).isTrue();
        }
    }
}
