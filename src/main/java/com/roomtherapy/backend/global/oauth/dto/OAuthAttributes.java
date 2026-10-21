package com.roomtherapy.backend.global.oauth.dto;

import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public class OAuthAttributes {

    private final Map<String, Object> attributes;
    private final String nameAttributeKey;
    private final String kakaoId;
    private final String nickname;
    private final String email;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey,
                           String kakaoId, String nickname, String email) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.email = email;
    }

    @SuppressWarnings("unchecked")
    public static OAuthAttributes of(String registrationId, String userNameAttributeName,
                                     Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            return ofKakao(userNameAttributeName, attributes);
        }
        throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다: " + registrationId);
    }

    @SuppressWarnings("unchecked")
    private static OAuthAttributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
        String kakaoId = String.valueOf(attributes.get("id"));

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = kakaoAccount != null ? (Map<String, Object>) kakaoAccount.get("profile") : null;
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");

        String nickname = "사용자";
        if (profile != null && profile.get("nickname") != null) {
            nickname = (String) profile.get("nickname");
        } else if (properties != null && properties.get("nickname") != null) {
            nickname = (String) properties.get("nickname");
        }

        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;

        return OAuthAttributes.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .email(email)
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    public User toEntity() {
        return User.builder()
                .kakaoId(kakaoId)
                .nickname(nickname)
                .email(email)
                .role(Role.USER)
                .build();
    }
}
