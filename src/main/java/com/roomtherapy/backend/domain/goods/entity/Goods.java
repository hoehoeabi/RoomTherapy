package com.roomtherapy.backend.domain.goods.entity;

import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "goods")
public class Goods extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price; // 리워드 상품인 경우 0원

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsStatus status;

    // 등록한 판매자 (추후 판매자/관리자 권한 분리용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @jakarta.persistence.OneToMany(mappedBy = "goods", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private java.util.List<GoodsAuthCode> authCodes = new java.util.ArrayList<>();

    @Builder
    public Goods(String name, String description, int price, String imageUrl,
                 int stockQuantity, GoodsStatus status, User seller) {
        this.name = name;
        this.description = description;
        this.price = Math.max(0, price);
        this.imageUrl = imageUrl;
        this.stockQuantity = Math.max(0, stockQuantity);
        this.status = status != null ? status : GoodsStatus.ACTIVE;
        this.seller = seller;
    }

    public void update(String name, String description, int price, String imageUrl, int stockQuantity, GoodsStatus status) {
        this.name = name;
        this.description = description;
        this.price = Math.max(0, price);
        this.imageUrl = imageUrl;
        this.stockQuantity = Math.max(0, stockQuantity);
        if (status != null) {
            this.status = status;
        }
    }

    public void decreaseStock(int quantity) {
        int restStock = this.stockQuantity - quantity;
        if (restStock < 0) {
            throw new IllegalStateException("재고가 부족합니다. (현재 재고: " + this.stockQuantity + ")");
        }
        this.stockQuantity = restStock;
        if (this.stockQuantity == 0) {
            this.status = GoodsStatus.SOLD_OUT;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return this.seller != null && this.seller.getId().equals(userId);
    }
}
