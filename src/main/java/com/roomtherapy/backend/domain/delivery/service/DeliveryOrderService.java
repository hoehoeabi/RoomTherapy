package com.roomtherapy.backend.domain.delivery.service;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelExportCondition;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderRequestDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderUpdateRequestDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.GoodsAuthCodeManager;
import com.roomtherapy.backend.domain.goods.service.RedisGoodsStockService;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import com.roomtherapy.backend.global.notification.KakaoNotificationService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final UserRepository userRepository;
    private final GoodsRepository goodsRepository;
    private final StageCodeService stageCodeService;
    private final GoodsAuthCodeManager goodsAuthCodeManager;
    private final RedisGoodsStockService redisGoodsStockService;
    private final KakaoNotificationService kakaoNotificationService;

    /**
     * 굿즈 배송 신청 처리 및 DB 저장
     */
    @Transactional
    public DeliveryOrderResponseDto createDeliveryOrder(Long userId, DeliveryOrderRequestDto request) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 동일 유저의 동일 인증코드 중복 신청 방지
        if (deliveryOrderRepository.existsByUserIdAndStageCode(userId, request.stageCode().trim())) {
            throw new BusinessException(ErrorCode.ALREADY_CLAIMED_CODE);
        }

        // 3. 굿즈 지정 배송 신청 시: 1인 1회 제한 + 굿즈별 인증코드 검증 + 선착순 재고 경합 제어
        Goods goods = null;
        if (request.goodsId() != null) {
            goods = goodsRepository.findById(request.goodsId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

            // 판매중(ACTIVE) 상태가 아니거나 재고가 소진된 상품은 배송 신청 불가
            if (goods.getStatus() != GoodsStatus.ACTIVE || goods.getStockQuantity() <= 0) {
                throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
            }

            // 1인 1회 신청 제한 (동일 굿즈 중복 수령 방지)
            if (deliveryOrderRepository.existsByUserIdAndGoodsId(userId, goods.getId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_GOODS_CLAIM);
            }

            // 굿즈 인증코드 적격성 검증 및 소진 (DB 판매자 코드 또는 Redis 동적 코드)
            goodsAuthCodeManager.validateAndConsume(goods.getId(), request.stageCode().trim(), userId);

            // Redis 기반 원자적 재고 차감 (선착순 경합 방지)
            boolean stockDecreased = redisGoodsStockService.tryDecreaseStock(goods.getId(), 1);
            if (!stockDecreased) {
                throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
            }

            // DB 엔티티 재고 차감 동기화
            goods.decreaseStock(1);
        } else {
            // 굿즈 미지정 신청인 경우 (기존 레거시 스테이지 코드 검증)
            stageCodeService.validateAndConsume(request.stageCode(), userId);
        }

        // 5. 배송 신청 엔티티 생성 및 DB 저장
        DeliveryOrder deliveryOrder = DeliveryOrder.builder()
                .user(user)
                .goods(goods)
                .stageCode(request.stageCode().trim())
                .recipientName(request.recipientName().trim())
                .phone(request.phone() != null ? request.phone().trim() : "")
                .zipcode(request.zipcode().trim())
                .address(request.address().trim())
                .detailAddress(request.detailAddress() != null ? request.detailAddress().trim() : "")
                .feedback(request.feedback() != null ? request.feedback().trim() : null)
                .status(DeliveryStatus.ORDERED)
                .build();

        DeliveryOrder savedOrder = deliveryOrderRepository.save(deliveryOrder);

        // 6. 사용자가 '기본 배송지로 저장'을 체크했거나, 기존 기본 배송지가 없으면 마이페이지 기본 배송지로 자동 동기화
        if (Boolean.TRUE.equals(request.saveAsDefaultAddress()) || user.getAddress() == null) {
            user.updateDefaultDeliveryInfo(
                    request.recipientName().trim(),
                    request.phone() != null ? request.phone().trim() : "",
                    request.zipcode().trim(),
                    request.address().trim(),
                    request.detailAddress() != null ? request.detailAddress().trim() : ""
            );
            userRepository.save(user);
            log.info("마이페이지 기본 배송지 정보 자동 동기화 완료 - UserId: {}", userId);
        }

        log.info("굿즈 배송 신청 DB 저장 완료 - OrderId: {}, UserId: {}, StageCode: {}",
                savedOrder.getId(), userId, savedOrder.getStageCode());

        // 7. 판매자에게 신규 굿즈 신청 카카오톡 실시간 알림 비동기 발송
        Long sellerId = resolveSellerId(savedOrder);
        if (sellerId != null) {
            String fullAddress = KakaoNotificationService.formatFullAddress(
                    savedOrder.getZipcode(), savedOrder.getAddress(), savedOrder.getDetailAddress());
            LocalDateTime orderedAt = savedOrder.getCreatedAt() != null ? savedOrder.getCreatedAt() : LocalDateTime.now();
            kakaoNotificationService.notifySellerOrderCreated(
                    sellerId,
                    savedOrder.getEffectiveGoodsName(),
                    fullAddress,
                    savedOrder.getRecipientName(),
                    orderedAt
            );
        }

        return DeliveryOrderResponseDto.from(savedOrder);
    }

    /**
     * 특정 배송 신청 단건 조회
     */
    @Transactional(readOnly = true)
    public DeliveryOrderResponseDto getOrder(Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return DeliveryOrderResponseDto.from(order);
    }

    /**
     * 현재 로그인한 사용자의 배송 신청 내역 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponseDto> getMyOrders(Long userId) {
        return deliveryOrderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DeliveryOrderResponseDto::from)
                .toList();
    }

    private static final DateTimeFormatter SUMMARY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 메인 화면 노출용 최신 주문 요약 정보 조회
     */
    @Transactional(readOnly = true)
    public Optional<String> findLatestOrderSummary(Long userId) {
        return deliveryOrderRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .findFirst()
                .map(order -> {
                    // 발송/배송 완료는 판매자가 처리한 시각(updatedAt), 그 외에는 신청 시각(createdAt) 기준
                    boolean shipped = order.getStatus() == DeliveryStatus.SHIPPED
                            || order.getStatus() == DeliveryStatus.DELIVERED;
                    LocalDateTime baseTime = (shipped && order.getUpdatedAt() != null)
                            ? order.getUpdatedAt()
                            : order.getCreatedAt();
                    String formattedDate = baseTime != null
                            ? baseTime.format(SUMMARY_DATE_FORMATTER)
                            : "";
                    return formattedDate + " 주문 건 현재 " + order.getStatus().getDescription();
                });
    }

    /**
     * 배송 주문 본인 소유 여부 검증 (SpEL / @PreAuthorize 연동)
     */
    @Transactional(readOnly = true)
    public boolean isOrderOwner(Long orderId, Object principal) {
        if (orderId == null) {
            return false;
        }
        if (principal instanceof CustomUserDetails userDetails) {
            return deliveryOrderRepository.findById(orderId)
                    .map(order -> order.getUser() != null && order.getUser().getId().equals(userDetails.getUserId()))
                    .orElse(true); // 주문 자체가 없으면 Controller에서 404 ORDER_NOT_FOUND 발생 유도
        }
        if (principal instanceof Long userId) {
            return deliveryOrderRepository.findById(orderId)
                    .map(order -> order.getUser() != null && order.getUser().getId().equals(userId))
                    .orElse(true);
        }
        // 미인증/익명 접근 시 주문이 존재하지 않는 경우엔 404 처리를 위해 통과 허용
        return deliveryOrderRepository.findById(orderId).isEmpty();
    }

    /**
     * 사용자의 배송지 정보 수정 처리 (ORDERED/PREPARING 상태만 가능)
     */
    @Transactional
    public DeliveryOrderResponseDto updateDeliveryOrder(Long userId, Long orderId, DeliveryOrderUpdateRequestDto request) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_ORDER_OWNER);
        }

        String oldRecipient = order.getRecipientName() != null ? order.getRecipientName().trim() : "";
        String oldFullAddress = KakaoNotificationService.formatFullAddress(
                order.getZipcode(), order.getAddress(), order.getDetailAddress());

        String newRecipient = request.recipientName().trim();
        String newPhone = request.phone() != null ? request.phone().trim() : "";
        String newZipcode = request.zipcode().trim();
        String newAddress = request.address().trim();
        String newDetailAddress = request.detailAddress() != null ? request.detailAddress().trim() : "";
        String newFullAddress = KakaoNotificationService.formatFullAddress(
                newZipcode, newAddress, newDetailAddress);

        boolean recipientChanged = !oldRecipient.equals(newRecipient);
        boolean addressChanged = !oldFullAddress.equals(newFullAddress);

        order.updateDeliveryInfo(newRecipient, newPhone, newZipcode, newAddress, newDetailAddress);

        log.info("배송 정보 사용자 수정 완료 - OrderId: {}, UserId: {}", orderId, userId);

        Long sellerId = resolveSellerId(order);
        if (sellerId != null) {
            kakaoNotificationService.notifySellerOrderModified(
                    sellerId,
                    order.getId(),
                    order.getEffectiveGoodsName(),
                    newFullAddress,
                    addressChanged,
                    newRecipient,
                    recipientChanged,
                    LocalDateTime.now()
            );
        }

        return DeliveryOrderResponseDto.from(order);
    }

    /**
     * 관리자용 전체 배송 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponseDto> getAllOrdersForAdmin() {
        return deliveryOrderRepository.findAllWithUserAndGoodsOrderByCreatedAtDesc().stream()
                .map(DeliveryOrderResponseDto::from)
                .toList();
    }

    /**
     * 관리자용 수정된 배송 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponseDto> getModifiedOrdersForAdmin() {
        return deliveryOrderRepository.findAllModifiedWithUserAndGoods().stream()
                .map(DeliveryOrderResponseDto::from)
                .toList();
    }

    /**
     * 관리자용 배송 상태 변경 (접수 -> 배송준비 -> 배송중 -> 배송완료)
     */
    @Transactional
    public void updateOrderStatus(Long orderId, DeliveryStatus newStatus) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.updateStatus(newStatus);
        log.info("배송 상태 업데이트 완료 - OrderId: {}, Status: {}", orderId, newStatus);
    }

    /**
     * 판매자용 본인 굿즈 배송 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponseDto> getOrdersForSeller(Long sellerId) {
        return deliveryOrderRepository.findAllBySellerId(sellerId).stream()
                .map(DeliveryOrderResponseDto::from)
                .toList();
    }

    /**
     * 판매자용 본인 굿즈 수정된 배송 신청 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderResponseDto> getModifiedOrdersForSeller(Long sellerId) {
        return deliveryOrderRepository.findModifiedBySellerId(sellerId).stream()
                .map(DeliveryOrderResponseDto::from)
                .toList();
    }

    /**
     * 판매자용 본인 굿즈 배송 상태 변경 처리
     */
    @Transactional
    public void updateOrderStatusBySeller(Long sellerId, Long orderId, DeliveryStatus newStatus) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getGoods() == null || order.getGoods().getSeller() == null || !order.getGoods().getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER_OWNER);
        }

        order.updateStatus(newStatus);
        log.info("판매자에 의한 배송 상태 업데이트 완료 - SellerId: {}, OrderId: {}, Status: {}", sellerId, orderId, newStatus);
    }

    private Long resolveSellerId(DeliveryOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getSeller() != null) {
            return order.getSeller().getId();
        }
        if (order.getGoods() != null && order.getGoods().getSeller() != null) {
            return order.getGoods().getSeller().getId();
        }
        return null;
    }

    /**
     * 관리자용 배송 주문 삭제 처리
     */
    @Transactional
    public void deleteOrder(Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        deliveryOrderRepository.delete(order);
        log.info("관리자에 의한 배송 주문 삭제 완료 - OrderId: {}", orderId);
    }

    /**
     * 판매자용 본인 굿즈 배송 주문 삭제 처리
     */
    @Transactional
    public void deleteOrderBySeller(Long sellerId, Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getGoods() == null || order.getGoods().getSeller() == null || !order.getGoods().getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.NOT_SELLER_OWNER);
        }

        deliveryOrderRepository.delete(order);
        log.info("판매자에 의한 배송 주문 삭제 완료 - SellerId: {}, OrderId: {}", sellerId, orderId);
    }

    /**
     * 관리자용 배송 신청 목록 페이징 및 날짜/수정/피드백 필터링 조회
     */
    @Transactional(readOnly = true)
    public Page<DeliveryOrderResponseDto> getAdminOrdersWithPaging(
            LocalDate startDate, LocalDate endDate, String filter, Pageable pageable) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        Boolean isModified = "modified".equalsIgnoreCase(filter) ? true : null;
        Boolean hasFeedback = "feedback".equalsIgnoreCase(filter) ? true : null;

        return deliveryOrderRepository.findAdminOrdersWithFilter(start, end, isModified, hasFeedback, pageable)
                .map(DeliveryOrderResponseDto::from);
    }

    /**
     * 판매자용 본인 굿즈 배송 신청 목록 페이징 및 날짜/수정/피드백 필터링 조회
     */
    @Transactional(readOnly = true)
    public Page<DeliveryOrderResponseDto> getSellerOrdersWithPaging(
            Long sellerId, LocalDate startDate, LocalDate endDate, String filter, Pageable pageable) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        Boolean isModified = "modified".equalsIgnoreCase(filter) ? true : null;
        Boolean hasFeedback = "feedback".equalsIgnoreCase(filter) ? true : null;

        return deliveryOrderRepository.findSellerOrdersWithFilter(sellerId, start, end, isModified, hasFeedback, pageable)
                .map(DeliveryOrderResponseDto::from);
    }

    /**
     * 사용자 마이페이지용 내 배송 신청 내역 페이징 조회
     */
    @Transactional(readOnly = true)
    public Page<DeliveryOrderResponseDto> getMyOrdersWithPaging(
            Long userId, Pageable pageable) {
        return deliveryOrderRepository.findMyOrdersWithPaging(userId, pageable)
                .map(DeliveryOrderResponseDto::from);
    }

    /**
     * 관리자용 엑셀 다운로드 필터 대상 주문 목록 조회 (기간, 요일, 굿즈, 상태, 수정여부)
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrder> getOrdersForAdminExport(com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelExportCondition condition) {
        List<DeliveryOrder> list = deliveryOrderRepository.findAllWithUserAndGoodsOrderByCreatedAtDesc();
        return filterExportOrders(list, condition);
    }

    /**
     * 판매자용 엑셀 다운로드 필터 대상 본인 굿즈 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrder> getOrdersForSellerExport(Long sellerId, DeliveryExcelExportCondition condition) {
        List<DeliveryOrder> list = deliveryOrderRepository.findAllBySellerId(sellerId);
        return filterExportOrders(list, condition);
    }

    private List<DeliveryOrder> filterExportOrders(List<DeliveryOrder> list, DeliveryExcelExportCondition condition) {
        if (condition == null) {
            return list;
        }

        return list.stream()
                .filter(o -> {
                    // 1. 굿즈 필터
                    if (condition.goodsId() != null) {
                        if (o.getGoods() == null || !condition.goodsId().equals(o.getGoods().getId())) {
                            return false;
                        }
                    }

                    // 2. 날짜 범위 필터
                    if (o.getCreatedAt() != null) {
                        LocalDate orderDate = o.getCreatedAt().toLocalDate();
                        if (condition.startDate() != null && orderDate.isBefore(condition.startDate())) {
                            return false;
                        }
                        if (condition.endDate() != null && orderDate.isAfter(condition.endDate())) {
                            return false;
                        }

                        // 3. 특정 요일 필터
                        if (condition.daysOfWeek() != null && !condition.daysOfWeek().isEmpty()) {
                            if (!condition.daysOfWeek().contains(orderDate.getDayOfWeek())) {
                                return false;
                            }
                        }
                    }

                    // 4. 배송 상태 필터
                    if (condition.status() != null && o.getStatus() != condition.status()) {
                        return false;
                    }

                    // 5. 수정 여부 / 피드백 작성 여부 필터
                    if ("modified".equalsIgnoreCase(condition.filter()) && !o.isModified()) {
                        return false;
                    }
                    if ("feedback".equalsIgnoreCase(condition.filter())
                            && (o.getFeedback() == null || o.getFeedback().isBlank())) {
                        return false;
                    }

                    return true;
                })
                .toList();
    }
}
