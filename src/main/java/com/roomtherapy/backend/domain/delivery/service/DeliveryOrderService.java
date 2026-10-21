package com.roomtherapy.backend.domain.delivery.service;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderRequestDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final UserRepository userRepository;
    private final GoodsRepository goodsRepository;
    private final StageCodeService stageCodeService;

    /**
     * 굿즈 배송 신청 처리 및 DB 저장
     */
    @Transactional
    public DeliveryOrderResponseDto createDeliveryOrder(Long userId, DeliveryOrderRequestDto request) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다: " + userId));

        // 2. 동일 유저의 동일 인증코드 중복 신청 방지
        if (deliveryOrderRepository.existsByUserIdAndStageCode(userId, request.stageCode().trim())) {
            throw new IllegalStateException("해당 인증코드로 이미 배송 신청을 완료하셨습니다.");
        }

        // 3. 인증코드 유효성 검증 및 소모 처리
        stageCodeService.validateAndConsume(request.stageCode(), userId);

        // 4. (선택적) 굿즈 조회 및 재고 차감
        Goods goods = null;
        if (request.goodsId() != null) {
            goods = goodsRepository.findById(request.goodsId())
                    .orElse(null);
            if (goods != null) {
                goods.decreaseStock(1);
            }
        }

        // 5. 배송 신청 엔티티 생성 및 DB 저장
        DeliveryOrder deliveryOrder = DeliveryOrder.builder()
                .user(user)
                .goods(goods)
                .stageCode(request.stageCode().trim())
                .recipientName(request.recipientName().trim())
                .phone(request.phone().trim())
                .zipcode(request.zipcode().trim())
                .address(request.address().trim())
                .detailAddress(request.detailAddress() != null ? request.detailAddress().trim() : "")
                .status(DeliveryStatus.ORDERED)
                .build();

        DeliveryOrder savedOrder = deliveryOrderRepository.save(deliveryOrder);

        log.info("굿즈 배송 신청 DB 저장 완료 - OrderId: {}, UserId: {}, StageCode: {}",
                savedOrder.getId(), userId, savedOrder.getStageCode());

        return DeliveryOrderResponseDto.from(savedOrder);
    }

    /**
     * 특정 배송 신청 단건 조회
     */
    @Transactional(readOnly = true)
    public DeliveryOrderResponseDto getOrder(Long orderId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("배송 신청 내역을 찾을 수 없습니다: " + orderId));
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

    /**
     * 사용자의 배송지 정보 수정 처리 (ORDERED/PREPARING 상태만 가능)
     */
    @Transactional
    public DeliveryOrderResponseDto updateDeliveryOrder(Long userId, Long orderId, com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderUpdateRequestDto request) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("배송 신청 내역을 찾을 수 없습니다: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 배송 신청 건만 수정할 수 있습니다.");
        }

        order.updateDeliveryInfo(
                request.recipientName().trim(),
                request.phone().trim(),
                request.zipcode().trim(),
                request.address().trim(),
                request.detailAddress() != null ? request.detailAddress().trim() : ""
        );

        log.info("배송 정보 사용자 수정 완료 - OrderId: {}, UserId: {}", orderId, userId);
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
                .orElseThrow(() -> new IllegalArgumentException("배송 내역을 찾을 수 없습니다: " + orderId));
        order.updateStatus(newStatus);
        log.info("배송 상태 업데이트 완료 - OrderId: {}, Status: {}", orderId, newStatus);
    }
}
