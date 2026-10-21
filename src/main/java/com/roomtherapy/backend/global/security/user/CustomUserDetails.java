package com.roomtherapy.backend.global.security.user;

import com.roomtherapy.backend.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String kakaoId;
    private final String nickname;
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.kakaoId = user.getKakaoId();
        this.nickname = user.getNickname();
        this.email = user.getEmail();
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole().getKey())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // OAuth2 기반이므로 패스워드 미사용
    }

    @Override
    public String getUsername() {
        return kakaoId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
