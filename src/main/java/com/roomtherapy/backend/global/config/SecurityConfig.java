package com.roomtherapy.backend.global.config;

import com.roomtherapy.backend.global.oauth.CustomOAuth2UserService;
import com.roomtherapy.backend.global.oauth.OAuth2SuccessHandler;
import com.roomtherapy.backend.global.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.roomtherapy.backend.global.security.handler.CustomAccessDeniedHandler;
import com.roomtherapy.backend.global.security.handler.CustomAuthenticationEntryPoint;

import java.util.Set;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 무상태 아키텍처)
                .csrf(AbstractHttpConfigurer::disable)
                // 세션 관리: 무상태(Stateless) 설정
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // H2 콘솔 iframe 허용
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                // 예외 처리 핸들러 등록 (401 미인증 및 403 인가 실패 친절한 한국어 안내 연동)
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(customAccessDeniedHandler)
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                // 접근 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/unity/goods/*/issue-code").authenticated()
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/images/**",
                                "/js/**",
                                "/h2-console",
                                "/h2-console/**",
                                "/login/**",
                                "/logout",
                                "/oauth2/**",
                                "/api/auth/**",
                                "/api/goods/**",
                                "/api/unity/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller/**").hasAnyRole("SELLER", "ADMIN")
                        .requestMatchers("/delivery/**", "/user/role", "/mypage/**").authenticated()
                        .anyRequest().permitAll()
                )
                // OAuth2 로그인 설정 (JWT 발급 핸들러 및 talk_message 추가 동의 리졸버 연동)
                .oauth2Login(oauth2 -> {
                    if (clientRegistrationRepository != null) {
                        oauth2.authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(kakaoAuthorizationRequestResolver(clientRegistrationRepository))
                        );
                    }
                    oauth2.userInfoEndpoint(userInfo -> userInfo
                                    .userService(customOAuth2UserService)
                            )
                            .successHandler(oAuth2SuccessHandler);
                })
                // JWT 인증 필터 등록
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 기본 세션 로그아웃 비활성화 (AuthController에서 커스텀 처리)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private OAuth2AuthorizationRequestResolver kakaoAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return customizeIfReconsent(request, defaultResolver.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return customizeIfReconsent(request, defaultResolver.resolve(request, clientRegistrationId));
            }

            private OAuth2AuthorizationRequest customizeIfReconsent(HttpServletRequest request, OAuth2AuthorizationRequest authRequest) {
                if (authRequest == null) {
                    return null;
                }
                if ("talk_message".equals(request.getParameter("reconsent"))) {
                    // 이미 로그인한 판매자/관리자에게 아이디/비번 재입력(prompt=login) 없이 talk_message 추가 동의 화면만 즉시 노출
                    return OAuth2AuthorizationRequest.from(authRequest)
                            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                            .scopes(Set.of("talk_message"))
                            .build();
                }
                return authRequest;
            }
        };
    }
}
