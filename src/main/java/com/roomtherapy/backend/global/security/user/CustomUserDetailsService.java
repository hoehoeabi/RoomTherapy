package com.roomtherapy.backend.global.security.user;

import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String kakaoId) throws UsernameNotFoundException {
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new UsernameNotFoundException("해당 카카오 ID 사용자를 찾을 수 없습니다: " + kakaoId));

        return new CustomUserDetails(user);
    }

    /**
     * JWT 검증 시 PK(userId) 기반 빠른 사용자 조회
     */
    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("해당 ID의 사용자를 찾을 수 없습니다: " + userId));

        return new CustomUserDetails(user);
    }
}
