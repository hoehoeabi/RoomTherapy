package com.roomtherapy.backend.global.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 카카오톡 실시간 알림 서비스 (나에게 보내기 API + 사용자별 카카오 OAuth2 토큰 활용)
 * - 비동기 전용 스레드 풀(kakao-notification-pool)을 사용하여 웹 요청 지연을 0초로 격리
 * - 카카오 Access Token 만료(401) 시 저장된 Refresh Token으로 자동 갱신 후 재발송(Retry)
 * - 알림 발송 실패 시에도 본래의 배송 신청/수정/상태변경 트랜잭션에 영향을 주지 않도록 완벽 예외 격리
 */
@Slf4j
@Service
public class KakaoNotificationService {

    private static final String KAKAO_SEND_MEMO_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send";
    private static final String KAKAO_TOKEN_REFRESH_URL = "https://kauth.kakao.com/oauth/token";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "kakao-notification-worker");
        t.setDaemon(true);
        return t;
    });

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final KakaoApiSender kakaoApiSender;

    @Value("${app.base-url:https://www.roomtherapy.kr}")
    private String baseUrl = "https://www.roomtherapy.kr";

    @Value("${spring.security.oauth2.client.registration.kakao.client-id:}")
    private String kakaoClientId = "";

    @Value("${spring.security.oauth2.client.registration.kakao.client-secret:}")
    private String kakaoClientSecret = "";

    @Autowired
    public KakaoNotificationService(UserRepository userRepository) {
        this(userRepository, new ObjectMapper(), new HttpKakaoApiSender());
    }

    public KakaoNotificationService(UserRepository userRepository, ObjectMapper objectMapper, KakaoApiSender kakaoApiSender) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.kakaoApiSender = kakaoApiSender;
    }

    /**
     * 1. [룸메이트 -> 판매자] 신규 굿즈 배송 신청 알림 (비동기 발송)
     */
    public void notifySellerOrderCreated(Long sellerId, String goodsName, String fullAddress, String recipientName, LocalDateTime orderedAt) {
        if (sellerId == null) {
            return;
        }
        CompletableFuture.runAsync(
                () -> sendOrderCreatedSync(sellerId, goodsName, fullAddress, recipientName, orderedAt),
                NOTIFICATION_EXECUTOR
        );
    }

    /**
     * 2. [룸메이트 -> 판매자] 배송 신청 내역(주소지·수령인) 수정 알림 (비동기 발송 - 옵션 A: 변경된 항목에만 (변경) 표시 + 최종값 출력)
     */
    public void notifySellerOrderModified(
            Long sellerId,
            Long orderId,
            String goodsName,
            String fullAddress,
            boolean addressChanged,
            String recipientName,
            boolean recipientChanged,
            LocalDateTime modifiedAt) {
        if (sellerId == null) {
            return;
        }
        CompletableFuture.runAsync(
                () -> sendOrderModifiedSync(sellerId, orderId, goodsName, fullAddress, addressChanged, recipientName, recipientChanged, modifiedAt),
                NOTIFICATION_EXECUTOR
        );
    }

    // =========================================================================
    // 동기 실행 및 메시지 포맷 빌더 (단위 테스트 및 내부 워커 호출용)
    // =========================================================================

    public void sendOrderCreatedSync(Long sellerId, String goodsName, String fullAddress, String recipientName, LocalDateTime orderedAt) {
        try {
            String text = buildOrderCreatedText(goodsName, fullAddress, recipientName, orderedAt);
            String targetUrl = normalizeBaseUrl() + "/seller/deliveries";
            sendKakaoTextMessageToUser(sellerId, text, "📦 판매자 배송 관리 바로가기", targetUrl);
        } catch (Exception e) {
            log.warn("[KakaoNotification] 판매자 신규 주문 알림 발송 중 예외 발생 (주문은 정상 처리됨) - sellerId: {}, cause: {}",
                    sellerId, e.getMessage());
        }
    }

    public void sendOrderModifiedSync(
            Long sellerId,
            Long orderId,
            String goodsName,
            String fullAddress,
            boolean addressChanged,
            String recipientName,
            boolean recipientChanged,
            LocalDateTime modifiedAt) {
        try {
            String text = buildOrderModifiedText(orderId, goodsName, fullAddress, addressChanged, recipientName, recipientChanged, modifiedAt);
            String targetUrl = normalizeBaseUrl() + "/seller/deliveries?filter=modified";
            sendKakaoTextMessageToUser(sellerId, text, "📋 수정된 주문 모아보기", targetUrl);
        } catch (Exception e) {
            log.warn("[KakaoNotification] 판매자 배송지 수정 알림 발송 중 예외 발생 (수정은 정상 처리됨) - sellerId: {}, orderId: {}, cause: {}",
                    sellerId, orderId, e.getMessage());
        }
    }

    public String buildOrderCreatedText(String goodsName, String fullAddress, String recipientName, LocalDateTime orderedAt) {
        String timeStr = (orderedAt != null ? orderedAt : LocalDateTime.now()).format(DATE_TIME_FORMATTER);
        String safeGoodsName = (goodsName != null && !goodsName.isBlank()) ? goodsName : "스테이지 기본 리워드";
        return "🔔 [룸테라피 신규 굿즈 신청 알림]\n"
                + "새로운 굿즈 배송 신청이 접수되었습니다!\n\n"
                + "• 신청 일시: " + timeStr + "\n"
                + "• 신청 굿즈: " + safeGoodsName + "\n"
                + "• 배송 주소: " + fullAddress + "\n"
                + "• 수령인: " + recipientName + " 님";
    }

    public String buildOrderModifiedText(
            Long orderId,
            String goodsName,
            String fullAddress,
            boolean addressChanged,
            String recipientName,
            boolean recipientChanged,
            LocalDateTime modifiedAt) {
        String timeStr = (modifiedAt != null ? modifiedAt : LocalDateTime.now()).format(DATE_TIME_FORMATTER);
        String safeGoodsName = (goodsName != null && !goodsName.isBlank()) ? goodsName : "스테이지 기본 리워드";
        String addressLabel = addressChanged ? "• 배송 주소(변경): " : "• 배송 주소: ";
        String recipientLabel = recipientChanged ? "• 수령인(변경): " : "• 수령인: ";

        return "✏️ [룸테라피 배송지 정보 수정 알림]\n"
                + "주문자님이 배송 신청 정보를 수정했습니다.\n"
                + "출고 전 변경된 내용을 꼭 확인해 주세요!\n\n"
                + "• 수정 일시: " + timeStr + "\n"
                + "• 주문 번호: #" + orderId + " (" + safeGoodsName + ")\n"
                + addressLabel + fullAddress + "\n"
                + recipientLabel + recipientName + " 님";
    }

    // =========================================================================
    // 내부 발송 및 토큰 자동 갱신(Auto-Refresh) 로직
    // =========================================================================

    private void sendKakaoTextMessageToUser(Long recipientUserId, String text, String buttonTitle, String targetUrl) throws Exception {
        Optional<User> userOpt = userRepository.findById(recipientUserId);
        if (userOpt.isEmpty()) {
            log.info("[KakaoNotification] 수신자(userId={})를 찾을 수 없어 카톡 알림을 건너뜁니다.", recipientUserId);
            return;
        }

        User recipient = userOpt.get();
        String accessToken = recipient.getKakaoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.info("[KakaoNotification] 수신자(userId={}, nickname={})의 카카오 메시지 토큰이 없어 카톡 알림을 건너뜁니다.",
                    recipientUserId, recipient.getNickname());
            return;
        }

        String templateObjectJson = buildTemplateObjectJson(text, buttonTitle, targetUrl);
        int statusCode = kakaoApiSender.sendMemo(accessToken, templateObjectJson);

        // Access Token 만료(401) 시 Refresh Token으로 자동 갱신 후 1회 재발송
        if (statusCode == 401 && recipient.getKakaoRefreshToken() != null && !recipient.getKakaoRefreshToken().isBlank()) {
            log.info("[KakaoNotification] 수신자(userId={})의 카카오 Access Token 만료 감지 -> Refresh Token으로 자동 갱신 시도", recipientUserId);
            String refreshedAccessToken = tryRefreshKakaoToken(recipient);
            if (refreshedAccessToken != null) {
                statusCode = kakaoApiSender.sendMemo(refreshedAccessToken, templateObjectJson);
            }
        }

        if (statusCode >= 200 && statusCode < 300) {
            log.info("[KakaoNotification] 카카오톡 알림 발송 성공 - recipientUserId: {}, button: {}", recipientUserId, buttonTitle);
        } else {
            log.warn("[KakaoNotification] 카카오톡 알림 발송 실패 (HTTP {}) - recipientUserId: {}", statusCode, recipientUserId);
        }
    }

    private String tryRefreshKakaoToken(User recipient) {
        try {
            String responseBody = kakaoApiSender.refreshAccessToken(
                    kakaoClientId,
                    kakaoClientSecret,
                    recipient.getKakaoRefreshToken()
            );
            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(responseBody);
            String newAccessToken = node.hasNonNull("access_token") ? node.get("access_token").asText() : null;
            String newRefreshToken = node.hasNonNull("refresh_token") ? node.get("refresh_token").asText() : null;
            if (newAccessToken != null && !newAccessToken.isBlank()) {
                recipient.updateKakaoTokens(newAccessToken, newRefreshToken);
                userRepository.save(recipient);
                log.info("[KakaoNotification] 수신자(userId={}) 카카오 토큰 자동 갱신 완료", recipient.getId());
                return newAccessToken;
            }
        } catch (Exception e) {
            log.warn("[KakaoNotification] 수신자(userId={}) 카카오 토큰 갱신 실패: {}", recipient.getId(), e.getMessage());
        }
        return null;
    }

    private String buildTemplateObjectJson(String text, String buttonTitle, String targetUrl) throws Exception {
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("web_url", targetUrl);
        link.put("mobile_web_url", targetUrl);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("object_type", "text");
        payload.put("text", text);
        payload.put("link", link);
        payload.put("button_title", buttonTitle);

        return objectMapper.writeValueAsString(payload);
    }

    private String normalizeBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://www.roomtherapy.kr";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static String formatFullAddress(String zipcode, String address, String detailAddress) {
        StringBuilder sb = new StringBuilder();
        if (zipcode != null && !zipcode.isBlank()) {
            sb.append("(").append(zipcode.trim()).append(") ");
        }
        if (address != null && !address.isBlank()) {
            sb.append(address.trim());
        }
        if (detailAddress != null && !detailAddress.isBlank()) {
            sb.append(" ").append(detailAddress.trim());
        }
        return sb.toString().trim();
    }

    /**
     * 카카오 API 통신 추상화 인터페이스 (단위 테스트에서 외부 네트워크 의존성 없이 완벽 검증 가능)
     */
    public interface KakaoApiSender {
        int sendMemo(String accessToken, String templateObjectJson) throws Exception;

        String refreshAccessToken(String clientId, String clientSecret, String refreshToken) throws Exception;
    }

    /**
     * 실제 Java HttpClient 기반 카카오 REST API 호출 구현체 (타임아웃 3초 격리)
     */
    public static class HttpKakaoApiSender implements KakaoApiSender {

        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        @Override
        public int sendMemo(String accessToken, String templateObjectJson) throws Exception {
            String formBody = "template_object=" + URLEncoder.encode(templateObjectJson, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KAKAO_SEND_MEMO_URL))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[KakaoApiSender] 나에게 보내기 API 응답 코드: {}, 바디: {}", response.statusCode(), response.body());
            }
            return response.statusCode();
        }

        @Override
        public String refreshAccessToken(String clientId, String clientSecret, String refreshToken) throws Exception {
            StringBuilder body = new StringBuilder("grant_type=refresh_token");
            body.append("&client_id=").append(URLEncoder.encode(clientId != null ? clientId : "", StandardCharsets.UTF_8));
            if (clientSecret != null && !clientSecret.isBlank()) {
                body.append("&client_secret=").append(URLEncoder.encode(clientSecret, StandardCharsets.UTF_8));
            }
            body.append("&refresh_token=").append(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KAKAO_TOKEN_REFRESH_URL))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.warn("[KakaoApiSender] 토큰 갱신 실패 (HTTP {}): {}", response.statusCode(), response.body());
            return null;
        }
    }
}
