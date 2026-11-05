package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeItemDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsUpdateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.UnityCodeIssueResponseDto;
import com.roomtherapy.backend.domain.goods.dto.UnityGoodsResponseDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.Goods3dObject;
import com.roomtherapy.backend.domain.goods.entity.GoodsAuthCode;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.Goods3dObjectRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.validator.RedisGoodsAuthCodeValidator;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final String CODE_PREFIX = "RT-";

    private final GoodsRepository goodsRepository;
    private final GoodsAuthCodeRepository goodsAuthCodeRepository;
    private final Goods3dObjectRepository goods3dObjectRepository;
    private final UserRepository userRepository;
    private final com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository deliveryOrderRepository;
    private final RedisGoodsStockService redisGoodsStockService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    /**
     * 판매자의 굿즈 및 인증코드 묶음 등록 처리
     */
    @Transactional
    public GoodsResponseDto registerGoods(Long sellerId, GoodsCreateRequestDto request) {
        return registerGoods(sellerId, request, List.of());
    }

    /**
     * 판매자의 굿즈, 인증코드 및 스테이지 3D 오브제 묶음 등록 처리
     */
    @Transactional
    public GoodsResponseDto registerGoods(Long sellerId, GoodsCreateRequestDto request, List<Goods3dObjectDto> objects3d) {
        User seller = null;
        if (sellerId != null) {
            seller = userRepository.findById(sellerId).orElse(null);
        }

        Goods goods = Goods.builder()
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : "")
                .price(request.price())
                .imageUrl(request.imageUrl() != null ? request.imageUrl().trim() : "")
                .stockQuantity(request.stockQuantity())
                .status(GoodsStatus.ACTIVE)
                .seller(seller)
                .build();

        Goods savedGoods = goodsRepository.save(goods);

        // 판매자가 입력한 인증코드 목록 일괄 저장
        List<String> codes = request.extractAuthCodes();
        if (!codes.isEmpty()) {
            List<GoodsAuthCode> authCodes = codes.stream()
                    .map(code -> GoodsAuthCode.builder()
                            .goods(savedGoods)
                            .code(code)
                            .build())
                    .toList();
            goodsAuthCodeRepository.saveAll(authCodes);
            log.info("굿즈 ID({})에 인증코드 {}개 등록 완료", savedGoods.getId(), authCodes.size());
        }

        // 스테이지 3D 오브제 목록 저장
        if (objects3d != null && !objects3d.isEmpty()) {
            syncGoods3dObjects(savedGoods, objects3d);
        }

        // Redis 재고 동기화 (선착순 경합 방지용)
        try {
            redisGoodsStockService.syncStock(savedGoods.getId(), savedGoods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패 (DB 저장은 정상 완료): {}", e.getMessage());
        }

        return GoodsResponseDto.from(savedGoods, codes);
    }

    /**
     * 유니티 연동용 굿즈 목록 조회 (활성 상태 굿즈)
     */
    @Transactional(readOnly = true)
    public List<UnityGoodsResponseDto> getGoodsForUnity() {
        return goodsRepository.findAll().stream()
                .filter(g -> g.getStatus() == GoodsStatus.ACTIVE)
                .map(UnityGoodsResponseDto::from)
                .toList();
    }

    /**
     * 유니티에서 스테이지 클리어 후 굿즈 PK로 동적 인증코드 발급 요청 처리 (Redis 기반)
     */
    public UnityCodeIssueResponseDto issueDynamicCodeForUnity(Long goodsId, Duration validDuration) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        if (goods.getStockQuantity() <= 0 || goods.getStatus() == GoodsStatus.SOLD_OUT) {
            throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
        }

        // 고유 난수 코드 생성 (예: RT-A7X9K2M3)
        StringBuilder sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        String generatedCode = sb.toString();

        // Redis에 저장: Key = goods:code:{code}, Value = goodsId, TTL 설정
        String redisKey = RedisGoodsAuthCodeValidator.REDIS_GOODS_CODE_PREFIX + generatedCode;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(goodsId), validDuration);

        log.info("유니티 동적 인증코드 발급 완료 - GoodsId: {}, Code: {}, TTL: {}분",
                goodsId, generatedCode, validDuration.toMinutes());

        return new UnityCodeIssueResponseDto(goods.getId(), goods.getName(), generatedCode, validDuration.toSeconds());
    }

    /**
     * 굿즈 단건 조회 (인증코드 목록 포함)
     */
    @Transactional(readOnly = true)
    public GoodsResponseDto getGoodsWithCodes(Long goodsId) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        List<String> codes = goodsAuthCodeRepository.findByGoodsId(goodsId).stream()
                .map(GoodsAuthCode::getCode)
                .toList();
        return GoodsResponseDto.from(goods, codes);
    }

    /**
     * 판매자/관리자가 관리 가능한 굿즈 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Goods> getManageableGoodsList(Long currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return goodsRepository.findAll();
        }
        if (currentUserId == null) {
            return List.of();
        }
        return goodsRepository.findAllBySellerId(currentUserId);
    }

    /**
     * 굿즈별 또는 전체 관리 대상 인증코드 상세 목록 조회 (소속 굿즈명 및 코드 ID 포함)
     */
    @Transactional(readOnly = true)
    public List<GoodsAuthCodeItemDto> getAuthCodeItems(Long goodsId, Long currentUserId, boolean isAdmin) {
        if (goodsId != null) {
            getGoodsForManagement(goodsId, currentUserId, isAdmin);
            return goodsAuthCodeRepository.findByGoodsIdWithGoods(goodsId).stream()
                    .map(GoodsAuthCodeItemDto::from)
                    .toList();
        }
        List<GoodsAuthCode> entities = isAdmin
                ? goodsAuthCodeRepository.findAllWithGoods()
                : goodsAuthCodeRepository.findAllBySellerIdWithGoods(currentUserId);
        return entities.stream()
                .map(GoodsAuthCodeItemDto::from)
                .toList();
    }

    /**
     * 특정 굿즈에 인증코드 추가 등록 (중복 코드는 자동 제외 후 신규 코드만 저장)
     */
    @Transactional
    public int addAuthCodesToGoods(Long goodsId, Long currentUserId, boolean isAdmin, String authCodesInput) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        if (authCodesInput == null || authCodesInput.isBlank()) {
            throw new IllegalArgumentException("추가할 인증코드를 1개 이상 입력해주세요.");
        }

        String[] tokens = authCodesInput.split("[\\r\\n,]+");
        List<String> validCodes = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();

        for (String token : tokens) {
            String trimmed = token != null ? token.trim() : "";
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase();
            if (!seenLower.contains(lower) && !goodsAuthCodeRepository.existsByGoodsIdAndCodeIgnoreCase(goodsId, trimmed)) {
                seenLower.add(lower);
                validCodes.add(trimmed);
            }
        }

        if (validCodes.isEmpty()) {
            return 0;
        }

        List<GoodsAuthCode> newEntities = validCodes.stream()
                .map(code -> GoodsAuthCode.builder()
                        .goods(goods)
                        .code(code)
                        .build())
                .toList();
        goodsAuthCodeRepository.saveAll(newEntities);
        log.info("굿즈 ID({}) '{}'에 인증코드 {}개 추가 등록 완료 - 처리자: {}",
                goodsId, goods.getName(), newEntities.size(), currentUserId);
        return newEntities.size();
    }

    /**
     * 특정 굿즈의 개별 인증코드 삭제 처리 (판매자 본인 또는 관리자)
     */
    @Transactional
    public String deleteGoodsAuthCode(Long goodsId, Long codeId, Long currentUserId, boolean isAdmin) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        GoodsAuthCode target = goodsAuthCodeRepository.findById(codeId)
                .filter(c -> c.getGoods() != null && c.getGoods().getId().equals(goodsId))
                .orElseThrow(() -> new IllegalArgumentException("삭제할 인증코드를 찾을 수 없습니다."));
        String deletedCode = target.getCode();
        goodsAuthCodeRepository.delete(target);
        log.info("굿즈 ID({}) '{}'의 인증코드 ID({}) '{}' 개별 삭제 완료 - 처리자: {}",
                goodsId, goods.getName(), codeId, deletedCode, currentUserId);
        return deletedCode;
    }

    /**
     * 굿즈 등록자(소유자) 여부 검증 (SpEL / @PreAuthorize 연동)
     */
    @Transactional(readOnly = true)
    public boolean isOwner(Long goodsId, Object principal) {
        if (goodsId == null) {
            return false;
        }
        if (principal instanceof com.roomtherapy.backend.global.security.user.CustomUserDetails userDetails) {
            return goodsRepository.findById(goodsId)
                    .map(goods -> goods.isOwnedBy(userDetails.getUserId()))
                    .orElse(false);
        }
        if (principal instanceof Long userId) {
            return goodsRepository.findById(goodsId)
                    .map(goods -> goods.isOwnedBy(userId))
                    .orElse(false);
        }
        return false;
    }

    /**
     * 굿즈 수정 화면용 조회 (권한 검증 포함)
     */
    @Transactional(readOnly = true)
    public Goods getGoodsForManagement(Long goodsId, Long currentUserId, boolean isAdmin) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        if (!goods.isManageableBy(currentUserId, isAdmin)) {
            throw new BusinessException(ErrorCode.NOT_GOODS_OWNER);
        }
        return goods;
    }

    /**
     * 굿즈 수정 처리 (권한 검증, 추가 인증코드 등록, Redis 재고 동기화)
     */
    @Transactional
    public GoodsResponseDto updateGoods(Long goodsId, Long currentUserId, boolean isAdmin, GoodsUpdateRequestDto request) {
        return updateGoods(goodsId, currentUserId, isAdmin, request, null);
    }

    /**
     * 굿즈 수정 및 스테이지 3D 오브제 동기화 처리 (추가/수정/삭제/정렬)
     */
    @Transactional
    public GoodsResponseDto updateGoods(Long goodsId, Long currentUserId, boolean isAdmin, GoodsUpdateRequestDto request, List<Goods3dObjectDto> objects3d) {
        if (request.imageUrl() == null || request.imageUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("굿즈 사진은 최소 1장 이상 등록되어야 합니다.");
        }

        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);

        goods.update(
                request.name().trim(),
                request.description() != null ? request.description().trim() : "",
                request.price(),
                request.imageUrl() != null ? request.imageUrl().trim() : "",
                request.stockQuantity(),
                request.status()
        );

        // 추가 인증코드가 입력된 경우 등록
        List<String> additionalCodes = request.extractAdditionalAuthCodes();
        if (!additionalCodes.isEmpty()) {
            List<GoodsAuthCode> newAuthCodes = additionalCodes.stream()
                    .map(code -> GoodsAuthCode.builder()
                            .goods(goods)
                            .code(code)
                            .build())
                    .toList();
            goodsAuthCodeRepository.saveAll(newAuthCodes);
            log.info("굿즈 ID({})에 추가 인증코드 {}개 등록 완료", goods.getId(), newAuthCodes.size());
        }

        // 스테이지 3D 오브제 목록 동기화 (명시적으로 전달된 경우 등록/수정/삭제 반영)
        if (objects3d != null) {
            syncGoods3dObjects(goods, objects3d);
        }

        // Redis 재고 동기화
        try {
            redisGoodsStockService.syncStock(goods.getId(), goods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패: {}", e.getMessage());
        }

        List<String> allCodes = goodsAuthCodeRepository.findByGoodsId(goods.getId()).stream()
                .map(GoodsAuthCode::getCode)
                .toList();
        return GoodsResponseDto.from(goods, allCodes);
    }

    /**
     * 굿즈의 3D 오브제 목록 동기화 (신규 등록, 기존 항목 수정, 제외된 항목 삭제)
     */
    @Transactional
    public void syncGoods3dObjects(Goods goods, List<Goods3dObjectDto> requestedObjects) {
        List<Goods3dObject> existingList = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(goods.getId());
        Map<Long, Goods3dObject> existingMap = existingList.stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(Goods3dObject::getId, Function.identity()));

        Set<Long> retainedIds = new HashSet<>();
        List<Goods3dObject> toSave = new ArrayList<>();
        int orderIndex = 0;

        if (requestedObjects != null) {
            for (Goods3dObjectDto dto : requestedObjects) {
                if (dto == null) {
                    continue;
                }
                String name = dto.name() != null ? dto.name().trim() : "";
                String description = dto.description() != null ? dto.description().trim() : "";
                String modelUrl = dto.modelUrl() != null ? dto.modelUrl().trim() : "";

                if (name.isEmpty() && modelUrl.isEmpty()) {
                    continue;
                }
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("3D 오브제의 이름을 입력해주세요.");
                }
                if (modelUrl.isEmpty() || modelUrl.startsWith("__NEW_MODEL_")) {
                    throw new IllegalArgumentException("3D 오브제 '" + name + "'의 3D 모델 파일을 업로드하거나 URL을 입력해주세요.");
                }

                if (dto.id() != null && existingMap.containsKey(dto.id())) {
                    Goods3dObject existing = existingMap.get(dto.id());
                    existing.update(name, description, modelUrl, orderIndex++);
                    retainedIds.add(existing.getId());
                    toSave.add(existing);
                } else {
                    Goods3dObject created = Goods3dObject.builder()
                            .goods(goods)
                            .name(name)
                            .description(description)
                            .modelUrl(modelUrl)
                            .displayOrder(orderIndex++)
                            .build();
                    toSave.add(created);
                }
            }
        }

        // 화면에서 삭제된 기존 3D 오브제 제거
        List<Goods3dObject> toDelete = existingList.stream()
                .filter(o -> o.getId() != null && !retainedIds.contains(o.getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            goods.getObjects3d().removeAll(toDelete);
            goods3dObjectRepository.deleteAll(toDelete);
            log.info("굿즈 ID({})에서 3D 오브제 {}개 삭제 완료", goods.getId(), toDelete.size());
        }

        if (!toSave.isEmpty()) {
            List<Goods3dObject> saved = goods3dObjectRepository.saveAll(toSave);
            for (Goods3dObject obj : saved) {
                if (!goods.getObjects3d().contains(obj)) {
                    goods.getObjects3d().add(obj);
                }
            }
            log.info("굿즈 ID({})에 3D 오브제 총 {}개 동기화(등록/수정) 완료", goods.getId(), saved.size());
        }
    }

    /**
     * 특정 굿즈의 3D 오브제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Goods3dObjectDto> getGoods3dObjects(Long goodsId) {
        return goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(goodsId).stream()
                .map(Goods3dObjectDto::from)
                .toList();
    }

    /**
     * 3D 전시관용 굿즈 단건 조회 (숨김 상품은 관리자 또는 소유 판매자만 조회 허용)
     */
    @Transactional(readOnly = true)
    public Goods getGoodsForExhibition(Long goodsId, Long currentUserId, boolean isAdmin) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        if (goods.getStatus() == GoodsStatus.INACTIVE && !goods.isManageableBy(currentUserId, isAdmin)) {
            throw new BusinessException(ErrorCode.GOODS_NOT_FOUND);
        }
        return goods;
    }

    /**
     * 단일 3D 오브제 개별 삭제 처리 (판매자 본인 또는 관리자)
     */
    @Transactional
    public void deleteGoods3dObject(Long goodsId, Long objectId, Long currentUserId, boolean isAdmin) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        Goods3dObject target = goods3dObjectRepository.findById(objectId)
                .filter(o -> o.getGoods() != null && o.getGoods().getId().equals(goodsId))
                .orElseThrow(() -> new IllegalArgumentException("삭제할 3D 오브제를 찾을 수 없습니다."));
        goods.getObjects3d().remove(target);
        goods3dObjectRepository.delete(target);
        log.info("굿즈 ID({})의 3D 오브제 ID({}) '{}' 개별 삭제 완료 - 처리자: {}",
                goodsId, objectId, target.getName(), currentUserId);
    }

    /**
     * 굿즈 재고 단독 변경 처리
     */
    @Transactional
    public void updateGoodsStock(Long goodsId, Long currentUserId, boolean isAdmin, int newStock) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        goods.updateStock(newStock);

        try {
            redisGoodsStockService.syncStock(goods.getId(), goods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패: {}", e.getMessage());
        }
    }

    /**
     * 굿즈 상태 변경 처리 (활성/품절/숨김, 권한 검증 포함)
     */
    @Transactional
    public void updateGoodsStatus(Long goodsId, Long currentUserId, boolean isAdmin, GoodsStatus newStatus) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        goods.updateStatus(newStatus);
        log.info("굿즈 ID({}) 상태 변경 완료: {} - 처리자: {}", goodsId, newStatus, currentUserId);
    }

    /**
     * 굿즈 삭제 처리 (권한 검증 및 배송 주문 외래키 해제 포함)
     */
    @Transactional
    public void deleteGoods(Long goodsId, Long currentUserId, boolean isAdmin) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);

        // 연동된 배송 주문 외래키(FK) 해제 및 주문 이력(굿즈명/판매자) 보존
        List<com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder> relatedOrders = deliveryOrderRepository.findByGoodsId(goodsId);
        for (com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder order : relatedOrders) {
            order.unlinkGoods();
        }

        goodsRepository.delete(goods);
        log.info("굿즈 ID({}) 및 연동 인증코드 삭제 완료 (연동 배송주문 {}건 FK 해제) - 처리자: {}", goodsId, relatedOrders.size(), currentUserId);
    }
}
