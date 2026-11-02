package com.roomtherapy.backend.domain.delivery.entity;

import com.roomtherapy.backend.domain.goods.entity.Goods;
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
import jakarta.persistence.Index;
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
@Table(
        name = "delivery_orders",
        indexes = {
                @Index(name = "idx_delivery_stage_code", columnList = "stageCode"),
                @Index(name = "idx_delivery_user_id", columnList = "user_id")
        }
)
public class DeliveryOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 배송 신청한 유저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 신청한 리워드/굿즈 물품 (선택적 연동)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods;

    // 해당 굿즈의 판매자 (굿즈 삭제 후에도 판매자 배송 이력 및 통계 보존용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    // 굿즈 명칭 스냅샷 (굿즈가 삭제되더라도 배송 조회 시 기존 굿즈명 유지)
    @Column(length = 100)
    private String snapshotGoodsName;

    // 유니티 게임에서 획득한 인증코드
    @Column(nullable = false, length = 100)
    private String stageCode;

    // 수령인 성함
    @Column(nullable = false, length = 50)
    private String recipientName;

    // 연락처 (일반우편 배송 시 선택 사항)
    @Column(length = 20)
    private String phone;

    // 우편번호
    @Column(nullable = false, length = 10)
    private String zipcode;

    // 기본 배송지 주소
    @Column(nullable = false, length = 255)
    private String address;

    // 상세 주소
    @Column(length = 255)
    private String detailAddress;

    // 유저 게임 피드백 (선택 작성)
    @Column(length = 500)
    private String feedback;

    // 배송 진행 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    // 사용자가 배송 정보를 수정한 적이 있는지 여부
    @Column(nullable = false)
    private boolean isModified = false;

    @Builder
    public DeliveryOrder(User user, Goods goods, User seller, String snapshotGoodsName,
                         String stageCode, String recipientName,
                         String phone, String zipcode, String address, String detailAddress,
                         String feedback, DeliveryStatus status) {
        this.user = user;
        this.goods = goods;
        this.seller = seller != null ? seller : (goods != null ? goods.getSeller() : null);
        this.snapshotGoodsName = snapshotGoodsName != null ? snapshotGoodsName : (goods != null ? goods.getName() : "스테이지 기본 리워드");
        this.stageCode = stageCode;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.feedback = feedback;
        this.status = status != null ? status : DeliveryStatus.ORDERED;
        this.isModified = false;
    }

    /**
     * 굿즈 삭제 시 외래키 제약조건 위반을 방지하고 배송 주문 이력을 안전하게 보존하기 위한 연결 해제
     */
    public void unlinkGoods() {
        if (this.goods != null) {
            if (this.snapshotGoodsName == null || this.snapshotGoodsName.isBlank()) {
                this.snapshotGoodsName = this.goods.getName();
            }
            if (this.seller == null) {
                this.seller = this.goods.getSeller();
            }
            this.goods = null;
        }
    }

    public boolean isGoodsDeleted() {
        return this.goods == null;
    }

    public boolean isGoodsInactive() {
        return this.goods != null && this.goods.getStatus() == com.roomtherapy.backend.domain.goods.entity.GoodsStatus.INACTIVE;
    }

    public String getEffectiveGoodsName() {
        if (this.goods != null) {
            return this.goods.getName();
        }
        return (this.snapshotGoodsName != null && !this.snapshotGoodsName.isBlank())
                ? this.snapshotGoodsName
                : "스테이지 기본 리워드";
    }

    public void updateStatus(DeliveryStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    public void updateDeliveryInfo(String recipientName, String phone, String zipcode, String address, String detailAddress) {
        if (this.status != DeliveryStatus.ORDERED && this.status != DeliveryStatus.PREPARING) {
            throw new com.roomtherapy.backend.global.error.BusinessException(com.roomtherapy.backend.global.error.ErrorCode.ORDER_CANNOT_BE_MODIFIED);
        }
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.isModified = true;
    }
}
