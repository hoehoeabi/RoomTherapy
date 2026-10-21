package com.roomtherapy.backend.domain.user.entity;

import com.roomtherapy.backend.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String kakaoId;

    @Column(nullable = false)
    private String nickname;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 사용자가 마이페이지에서 닉네임을 직접 수정했는지 여부 (카카오 재로그인 시 덮어쓰기 방지)
    @Column(nullable = false)
    private boolean isCustomNickname = false;

    // 마이페이지에 저장해두는 기본 수령인 성함
    @Column(length = 50)
    private String recipientName;

    // 마이페이지에 저장해두는 기본 연락처 (010-XXXX-XXXX)
    @Column(length = 20)
    private String phone;

    // 마이페이지에 저장해두는 기본 우편번호 (5자리)
    @Column(length = 10)
    private String zipcode;

    // 마이페이지에 저장해두는 기본 배송지 주소
    @Column(length = 255)
    private String address;

    // 마이페이지에 저장해두는 상세 주소
    @Column(length = 255)
    private String detailAddress;

    @Builder
    public User(String kakaoId, String nickname, String email, Role role,
                String recipientName, String phone, String zipcode, String address, String detailAddress) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.email = email;
        this.role = role != null ? role : Role.USER;
        this.isCustomNickname = false;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
    }

    /**
     * 카카오 OAuth 로그인 시 자동 갱신 (사용자가 닉네임을 커스텀 변경하지 않은 경우에만 동기화)
     */
    public User update(String nickname, String email) {
        if (!this.isCustomNickname && nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        return this;
    }

    /**
     * 마이페이지에서 사용자 닉네임 변경
     */
    public void updateNickname(String newNickname) {
        if (newNickname != null && !newNickname.isBlank()) {
            this.nickname = newNickname.trim();
            this.isCustomNickname = true;
        }
    }

    /**
     * 마이페이지 기본 배송지 정보 저장/수정
     */
    public void updateDefaultDeliveryInfo(String recipientName, String phone, String zipcode, String address, String detailAddress) {
        this.recipientName = recipientName != null ? recipientName.trim() : null;
        this.phone = phone != null ? phone.trim() : null;
        this.zipcode = zipcode != null ? zipcode.trim() : null;
        this.address = address != null ? address.trim() : null;
        this.detailAddress = detailAddress != null ? detailAddress.trim() : null;
    }

    public void updateRole(Role role) {
        this.role = role;
    }
}
