package com.roomtherapy.backend.domain.delivery.repository;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    // 동일 유저가 동일 코드로 이미 신청했는지 검증
    boolean existsByUserIdAndStageCode(Long userId, String stageCode);

    // 동일 유저가 동일 굿즈를 이미 신청했는지 검증 (중복 수령 방지)
    boolean existsByUserIdAndGoodsId(Long userId, Long goodsId);

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
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods g " +
           "WHERE (d.seller.id = :sellerId OR (g IS NOT NULL AND g.seller.id = :sellerId)) ORDER BY d.createdAt DESC")
    List<DeliveryOrder> findAllBySellerId(@Param("sellerId") Long sellerId);

    // 판매자용 수정된 본인 굿즈 배송 신청 목록 조회
    @Query("SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods g " +
           "WHERE (d.seller.id = :sellerId OR (g IS NOT NULL AND g.seller.id = :sellerId)) AND d.isModified = true ORDER BY d.updatedAt DESC")
    List<DeliveryOrder> findModifiedBySellerId(@Param("sellerId") Long sellerId);

    // 관리자용 배송 신청 목록 페이징 및 날짜/수정여부/피드백유무 필터 조회
    @Query(value = "SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods g " +
            "WHERE (:start IS NULL OR d.createdAt >= :start) " +
            "AND (:end IS NULL OR d.createdAt <= :end) " +
            "AND (:isModified IS NULL OR d.isModified = :isModified) " +
            "AND (:hasFeedback IS NULL OR (:hasFeedback = true AND d.feedback IS NOT NULL AND TRIM(d.feedback) <> '')) " +
            "ORDER BY d.createdAt DESC",
            countQuery = "SELECT count(d) FROM DeliveryOrder d " +
            "WHERE (:start IS NULL OR d.createdAt >= :start) " +
            "AND (:end IS NULL OR d.createdAt <= :end) " +
            "AND (:isModified IS NULL OR d.isModified = :isModified) " +
            "AND (:hasFeedback IS NULL OR (:hasFeedback = true AND d.feedback IS NOT NULL AND TRIM(d.feedback) <> ''))")
    Page<DeliveryOrder> findAdminOrdersWithFilter(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("isModified") Boolean isModified,
            @Param("hasFeedback") Boolean hasFeedback,
            Pageable pageable
    );

    // 판매자용 본인 굿즈 배송 신청 목록 페이징 및 날짜/수정여부/피드백유무 필터 조회
    @Query(value = "SELECT d FROM DeliveryOrder d JOIN FETCH d.user LEFT JOIN FETCH d.goods g " +
            "WHERE (d.seller.id = :sellerId OR (g IS NOT NULL AND g.seller.id = :sellerId)) " +
            "AND (:start IS NULL OR d.createdAt >= :start) " +
            "AND (:end IS NULL OR d.createdAt <= :end) " +
            "AND (:isModified IS NULL OR d.isModified = :isModified) " +
            "AND (:hasFeedback IS NULL OR (:hasFeedback = true AND d.feedback IS NOT NULL AND TRIM(d.feedback) <> '')) " +
            "ORDER BY d.createdAt DESC",
            countQuery = "SELECT count(d) FROM DeliveryOrder d LEFT JOIN d.goods g " +
            "WHERE (d.seller.id = :sellerId OR (g IS NOT NULL AND g.seller.id = :sellerId)) " +
            "AND (:start IS NULL OR d.createdAt >= :start) " +
            "AND (:end IS NULL OR d.createdAt <= :end) " +
            "AND (:isModified IS NULL OR d.isModified = :isModified) " +
            "AND (:hasFeedback IS NULL OR (:hasFeedback = true AND d.feedback IS NOT NULL AND TRIM(d.feedback) <> ''))")
    Page<DeliveryOrder> findSellerOrdersWithFilter(
            @Param("sellerId") Long sellerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("isModified") Boolean isModified,
            @Param("hasFeedback") Boolean hasFeedback,
            Pageable pageable
    );

    // 특정 굿즈와 연계된 배송 주문 목록 조회
    List<DeliveryOrder> findByGoodsId(Long goodsId);

    // 사용자 마이페이지용 내 배송 신청 목록 페이징 조회
    @Query(value = "SELECT d FROM DeliveryOrder d LEFT JOIN FETCH d.goods " +
            "WHERE d.user.id = :userId ORDER BY d.createdAt DESC",
            countQuery = "SELECT count(d) FROM DeliveryOrder d WHERE d.user.id = :userId")
    Page<DeliveryOrder> findMyOrdersWithPaging(
            @Param("userId") Long userId,
            Pageable pageable
    );

    // 상태별 조회
    List<DeliveryOrder> findAllByStatusOrderByCreatedAtDesc(DeliveryStatus status);
}
