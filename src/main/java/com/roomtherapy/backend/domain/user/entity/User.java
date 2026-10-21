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

    @Builder
    public User(String kakaoId, String nickname, String email, Role role) {
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.email = email;
        this.role = role != null ? role : Role.USER;
    }

    public User update(String nickname, String email) {
        this.nickname = nickname;
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        return this;
    }

    public void updateRole(Role role) {
        this.role = role;
    }
}
