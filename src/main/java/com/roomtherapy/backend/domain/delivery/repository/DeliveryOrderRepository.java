package com.roomtherapy.backend.domain.delivery.repository;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    // 동일 유저가 동일 코드로 이미 신청했는지 검증
    boolean existsByUserIdAndStageCode(Long userId, String stageCode);

    // 해당 인증코드가 이미 누군가에 의해 사용되었는지 검증
    boolean existsByStageCode(String stageCode);

    // 유저별 배송 신청 내역 조회 (최신순)
    List<DeliveryOrder> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    // 관리자용 전체 배송 목록 조회 (유저 및 굿즈 Fetch Join으로 N+1 방지)
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods ORDER BY d.createdAt DESC")
    List<DeliveryOrder> findAllWithUserAndGoodsOrderByCreatedAtDesc();

    // 관리자용 수정된 배송 신청 목록 조회 (수정 최신순)
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods WHERE d.isModified = true ORDER BY d.updatedAt DESC")
    List<DeliveryOrder> findAllModifiedWithUserAndGoods();

    // 판매자용 본인 굿즈 배송 신청 목록 조회
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user JOIN FETCH d.goods g WHERE g.seller.id = :sellerId ORDER BY d.createdAt DESC")
    List<DeliveryOrder> findAllBySellerId(@org.springframework.data.repository.query.Param("sellerId") Long sellerId);

    // 판매자용 수정된 본인 굿즈 배송 신청 목록 조회
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user JOIN FETCH d.goods g WHERE g.seller.id = :sellerId AND d.isModified = true ORDER BY d.updatedAt DESC")
    List<DeliveryOrder> findModifiedBySellerId(@org.springframework.data.repository.query.Param("sellerId") Long sellerId);

    // 상태별 조회
    List<DeliveryOrder> findAllByStatusOrderByCreatedAtDesc(DeliveryStatus status);
}
