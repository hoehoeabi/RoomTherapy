package com.roomtherapy.backend.global.oauth;

import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.oauth.dto.OAuthAttributes;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${admin.kakao-ids:}")
    private java.util.List<String> adminKakaoIds;

    @org.springframework.beans.factory.annotation.Value("${seller.kakao-ids:}")
    private java.util.List<String> sellerKakaoIds;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        User user = saveOrUpdate(attributes);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().getKey())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey()
        );
    }

    private User saveOrUpdate(OAuthAttributes attributes) {
        boolean isAdmin = adminKakaoIds != null && adminKakaoIds.contains(attributes.getKakaoId());
        boolean isSeller = sellerKakaoIds != null && sellerKakaoIds.contains(attributes.getKakaoId());

        User user = userRepository.findByKakaoId(attributes.getKakaoId())
                .map(entity -> {
                    entity.update(attributes.getNickname(), attributes.getEmail());
                    if (isAdmin) {
                        entity.updateRole(com.roomtherapy.backend.domain.user.entity.Role.ADMIN);
                    } else if (isSeller && entity.getRole() != com.roomtherapy.backend.domain.user.entity.Role.ADMIN) {
                        entity.updateRole(com.roomtherapy.backend.domain.user.entity.Role.SELLER);
                    }
                    return entity;
                })
                .orElseGet(() -> {
                    User newUser = attributes.toEntity();
                    if (isAdmin) {
                        newUser.updateRole(com.roomtherapy.backend.domain.user.entity.Role.ADMIN);
                    } else if (isSeller) {
                        newUser.updateRole(com.roomtherapy.backend.domain.user.entity.Role.SELLER);
                    }
                    return newUser;
                });

        return userRepository.save(user);
    }
}
