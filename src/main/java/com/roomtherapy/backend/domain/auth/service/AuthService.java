package com.roomtherapy.backend.domain.auth.service;

import com.roomtherapy.backend.domain.auth.dto.TokenResponseDto;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.jwt.RefreshToken;
import com.roomtherapy.backend.global.security.jwt.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /**
     * RTR (Refresh Token Rotation) 기반 토큰 재발급
     */
    @Transactional
    public TokenResponseDto reissue(String refreshToken) {
        // 1. Refresh Token 자체 서명 및 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다.");
        }

        // 2. 토큰에서 User ID 추출
        Long userId = jwtProvider.getUserId(refreshToken);

        // 3. Redis에서 해당 유저의 Refresh Token 조회
        RefreshToken savedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Redis에 저장된 Refresh Token이 존재하지 않습니다. User ID: {}", userId);
                    return new IllegalArgumentException("로그아웃되었거나 만료된 세션입니다. 다시 로그인해 주세요.");
                });

        // 4. [RTR 탈취 감지]
        // 클라이언트가 제시한 토큰과 Redis에 보관된 최신 토큰이 불일치하면 이전 토큰이 탈취되어 재사용된 것으로 간주
        if (!savedToken.getToken().equals(refreshToken)) {
            log.error("🚨 [탈취 감지] 저장된 토큰과 일치하지 않는 이전 Refresh Token 사용 시도! User ID: {}", userId);
            // 해당 사용자의 모든 세션 무효화 (Redis에서 삭제)
            refreshTokenRepository.delete(savedToken);
            throw new IllegalStateException("토큰 탈취 위험이 감지되어 강제 로그아웃되었습니다.");
        }

        // 5. 유저 정보 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 6. 신규 Access Token & Refresh Token 생성 (RTR)
        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefreshToken = jwtProvider.createRefreshToken(userId);

        // 7. Redis의 Refresh Token 갱신
        savedToken.updateToken(newRefreshToken, jwtProvider.getRefreshTokenExpirationSec());
        refreshTokenRepository.save(savedToken);

        log.info("RTR 토큰 재발급 완료 - User ID: {}", userId);

        return new TokenResponseDto(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃: Redis에서 Refresh Token 삭제
     */
    @Transactional
    public void logout(Long userId) {
        if (userId != null) {
            refreshTokenRepository.deleteById(userId);
            log.info("Redis RefreshToken 삭제 완료 - User ID: {}", userId);
        }
    }
}
