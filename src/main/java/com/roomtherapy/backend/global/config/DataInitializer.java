package com.roomtherapy.backend.global.config;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final GoodsRepository goodsRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. 테스트 판매자 계정 생성
        User seller = userRepository.findByKakaoId("test_seller_1001")
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId("test_seller_1001")
                        .nickname("포근한 공방 (판매자)")
                        .email("seller@roomtherapy.test")
                        .role(Role.SELLER)
                        .build()));

        // 2. 테스트 관리자 계정 생성
        userRepository.findByKakaoId("test_admin_2002")
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId("test_admin_2002")
                        .nickname("룸테라피 마스터 (어드민)")
                        .email("admin@roomtherapy.test")
                        .role(Role.ADMIN)
                        .build()));

        // 3. 테스트 일반 유저 계정 생성
        userRepository.findByKakaoId("test_user_3003")
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId("test_user_3003")
                        .nickname("초보 힐러 (일반유저)")
                        .email("user@roomtherapy.test")
                        .role(Role.USER)
                        .build()));

        // 4. 샘플 굿즈(리워드 제품) 등록
        if (goodsRepository.count() == 0) {
            goodsRepository.save(Goods.builder()
                    .name("💡 룸테라피 아늑한 무드등 (스테이지 1 리워드)")
                    .description("은은하고 따뜻한 주황빛으로 지친 하루를 편안하게 밝혀주는 침실 감성 무드등입니다.")
                    .price(0)
                    .stockQuantity(50)
                    .status(GoodsStatus.ACTIVE)
                    .seller(seller)
                    .build());

            goodsRepository.save(Goods.builder()
                    .name("🧸 포근 힐링 담요 & 수면 안대 세트")
                    .description("방 꾸미기를 완료한 당신을 위한 부드러운 극세사 무릎 담요와 암막 수면 안대 패키지입니다.")
                    .price(0)
                    .stockQuantity(30)
                    .status(GoodsStatus.ACTIVE)
                    .seller(seller)
                    .build());

            goodsRepository.save(Goods.builder()
                    .name("🌲 시그니처 숲속 편백 룸 스프레이")
                    .description("숲속 테마 룸 클리어 기념! 방 안 가득 청량한 피톤치드 향기를 선사하는 천연 룸 스프레이입니다.")
                    .price(0)
                    .stockQuantity(100)
                    .status(GoodsStatus.ACTIVE)
                    .seller(seller)
                    .build());

            goodsRepository.save(Goods.builder()
                    .name("🌱 나만의 작은 정원 미니 화분 키트")
                    .description("책상 위 작은 쉼터가 되어줄 반려식물 새싹 키트 (화분, 압축배양토, 씨앗 포함)입니다.")
                    .price(0)
                    .stockQuantity(25)
                    .status(GoodsStatus.ACTIVE)
                    .seller(seller)
                    .build());

            log.info("테스트 데이터 초기화 완료: 판매자/관리자/유저 계정 및 샘플 굿즈 4종 등록");
        }
    }
}
