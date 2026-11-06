/**
 * ============================================================================
 * mypage.js - 사용자 마이페이지 자바스크립트
 * ============================================================================
 *
 * [적용 화면]
 * - 마이페이지 화면: /mypage (src/main/resources/templates/user/mypage.html)
 *
 * [주요 기능]
 * 1. 탭 전환 제어 (switchTab: 'profile' 프로필 관리 vs 'orders' 배송 내역)
 * 2. URL 파라미터 기반 탭 상태 동기화 및 자동 활성화
 * 3. 카카오 Daum 우편번호 & 기본 배송지 검색 팝업 (openDaumPostcode)
 * ============================================================================
 */

/* 1. 새로고침 없는 원터치 탭 전환 함수 */
function switchTab(tabName) {
    const profileContent = document.getElementById('tab-profile-content');
    const ordersContent = document.getElementById('tab-orders-content');
    const btnProfile = document.getElementById('tab-btn-profile');
    const btnOrders = document.getElementById('tab-btn-orders');

    if (!profileContent || !ordersContent) return;

    if (tabName === 'profile') {
        profileContent.style.display = 'block';
        ordersContent.style.display = 'none';
        if (btnProfile) btnProfile.className = 'tab-btn active';
        if (btnOrders) btnOrders.className = 'tab-btn';
    } else {
        profileContent.style.display = 'none';
        ordersContent.style.display = 'block';
        if (btnProfile) btnProfile.className = 'tab-btn';
        if (btnOrders) btnOrders.className = 'tab-btn active';
    }
    // URL 파라미터 갱신 (뒤로가기/새로고침 호환)
    window.history.replaceState(null, '', '/mypage?tab=' + tabName);
}

/* 2. Daum 우편번호 검색 API */
function openDaumPostcode() {
    if (typeof daum === 'undefined' || !daum.Postcode) {
        alert('주소 검색 서비스를 불러오는 중입니다. 잠시 후 다시 시도해주세요.');
        return;
    }
    new daum.Postcode({
        oncomplete: function(data) {
            const fullAddr = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;
            const zipEl = document.getElementById('zipcode');
            const addrEl = document.getElementById('address');
            const detailEl = document.getElementById('detailAddress');
            if (zipEl) zipEl.value = data.zonecode;
            if (addrEl) addrEl.value = fullAddr;
            if (detailEl) detailEl.focus();
        }
    }).open();
}

/* 3. 페이지 진입 시 URL 파라미터에 따른 탭 자동 선택 */
document.addEventListener("DOMContentLoaded", function() {
    const params = new URLSearchParams(window.location.search);
    const currentTab = params.get('tab');
    if (currentTab === 'orders') {
        switchTab('orders');
    }
});
