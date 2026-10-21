package com.roomtherapy.backend.domain.goods.entity;

import com.roomtherapy.backend.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 굿즈 등록 시 함께 등록하는 유효 인증코드 목록
 * 유니티에 하드코딩된 인증코드 묶음과 1:1 매칭되어 유효성을 검증합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "goods_auth_code",
        indexes = {
                @Index(name = "idx_goods_auth_code", columnList = "goods_id, code")
        }
)
public class GoodsAuthCode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @Column(nullable = false, length = 100)
    private String code;

    @Builder
    public GoodsAuthCode(Goods goods, String code) {
        this.goods = goods;
        this.code = code != null ? code.trim() : "";
    }
}
