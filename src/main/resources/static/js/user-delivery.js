/**
 * ============================================================================
 * user-delivery.js - 사용자 배송 신청/수정/완료 모바일 뷰 자바스크립트
 * ============================================================================
 *
 * [적용 화면]
 * - 배송 신청 화면: /delivery (src/main/resources/templates/delivery/apply.html)
 * - 배송 정보 수정 화면: /delivery/edit/{id} (src/main/resources/templates/delivery/edit.html)
 * - 배송 신청 완료 화면: /delivery/confirm/{id} (src/main/resources/templates/delivery/confirm.html)
 *
 * [주요 기능]
 * 1. 카카오 Daum Postcode 우편번호/주소 검색 팝업 (openDaumPostcode)
 * 2. 리워드 상품 셀렉트박스 변경 시 썸네일 슬라이더 갱신 (onGoodsSelectChange)
 * 3. 게임 클리어 인증코드 실시간 유효성 사전 검증 API 호출 (verifyAuthCode)
 * 4. 가로 썸네일 이미지 슬라이더 터치/마우스 드래그 스크롤 (initThumbSliderDrag)
 * 5. 전화번호 3분할 입력 필드 동기화 (initPhoneInputSync, syncPhoneNumber)
 * ============================================================================
 */

/* 1. 카카오 Daum 우편번호 & 주소 검색 */
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

/* 2. 굿즈 셀렉트 선택 변경 시 썸네일 슬라이더 이미지 목록 갱신 */
function onGoodsSelectChange(selectElem) {
    const slider = document.querySelector('.thumb-slider');
    if (!slider || !selectElem) return;
    const selectedOpt = selectElem.options[selectElem.selectedIndex];
    const imagesStr = selectedOpt ? (selectedOpt.getAttribute('data-img') || '') : '';
    const goodsName = selectedOpt ? selectedOpt.text.split(' (')[0] : '리워드';

    slider.innerHTML = '';
    if (imagesStr && imagesStr.trim().length > 0) {
        const urls = imagesStr.split(',').map(s => s.trim()).filter(s => s.length > 0);
        urls.forEach(url => {
            const box = document.createElement('div');
            box.className = 'thumb-box';
            const img = document.createElement('img');
            img.src = url;
            img.alt = goodsName;
            box.appendChild(img);
            slider.appendChild(box);
        });
    } else {
        const box = document.createElement('div');
        box.className = 'thumb-box';
        const span = document.createElement('span');
        span.className = 'thumb-placeholder';
        span.textContent = '🎁';
        box.appendChild(span);
        slider.appendChild(box);
    }
}

/* 3. 인증코드 실시간 유효성 검증 API */
async function verifyAuthCode() {
    const codeInput = document.getElementById('stageCode');
    const goodsSelect = document.getElementById('goodsId');
    const feedback = document.getElementById('codeVerifyFeedback');
    if (!codeInput || !feedback) return;

    const code = codeInput.value.trim();
    const goodsId = goodsSelect ? goodsSelect.value : '';

    if (!code) {
        feedback.style.display = 'block';
        feedback.style.background = '#FFEBEB';
        feedback.style.color = 'var(--red)';
        feedback.innerHTML = '❌ 인증코드를 입력해주세요.';
        codeInput.style.borderColor = 'var(--red)';
        return;
    }

    feedback.style.display = 'block';
    feedback.style.background = '#F8F9FA';
    feedback.style.color = 'var(--gray)';
    feedback.innerHTML = '⏳ 인증코드 유효성 검사 중...';

    try {
        let url = `/api/goods/verify-code?code=${encodeURIComponent(code)}`;
        if (goodsId) {
            url += `&goodsId=${encodeURIComponent(goodsId)}`;
        }
        const res = await fetch(url);
        const result = await res.json();

        if (result.data && result.data.valid) {
            feedback.style.background = '#ECFDF5';
            feedback.style.color = '#059669';
            feedback.innerHTML = `✅ ${result.data.message}`;
            codeInput.style.borderColor = '#10B981';
        } else {
            feedback.style.background = '#FFEBEB';
            feedback.style.color = 'var(--red)';
            feedback.innerHTML = `❌ ${result.data ? result.data.message : '유효하지 않은 인증코드입니다.'}`;
            codeInput.style.borderColor = 'var(--red)';
        }
    } catch (err) {
        feedback.style.background = '#FFEBEB';
        feedback.style.color = 'var(--red)';
        feedback.innerHTML = '❌ 검증 중 통신 오류가 발생했습니다.';
    }
}

/* 4. 가로 썸네일 슬라이더 마우스 드래그 스크롤 바인딩 */
function initThumbSliderDrag() {
    const slider = document.querySelector('.thumb-slider');
    if (!slider) return;

    let isDown = false;
    let startX, scrollLeft;
    slider.addEventListener('mousedown', (e) => {
        isDown = true;
        slider.classList.add('dragging');
        startX = e.pageX - slider.offsetLeft;
        scrollLeft = slider.scrollLeft;
    });
    slider.addEventListener('mouseleave', () => {
        isDown = false;
        slider.classList.remove('dragging');
    });
    slider.addEventListener('mouseup', () => {
        isDown = false;
        slider.classList.remove('dragging');
    });
    slider.addEventListener('mousemove', (e) => {
        if (!isDown) return;
        e.preventDefault();
        const x = e.pageX - slider.offsetLeft;
        const walk = (x - startX) * 1.5;
        slider.scrollLeft = scrollLeft - walk;
    });
}

/* 5. 전화번호 3분할 입력 필드 동기화 */
function syncPhoneNumber() {
    const p1 = document.getElementById("phone1");
    const p2 = document.getElementById("phone2");
    const p3 = document.getElementById("phone3");
    const phone = document.getElementById("phone");
    if (p1 && p2 && p3 && phone) {
        phone.value = p1.value.trim() + "-" + p2.value.trim() + "-" + p3.value.trim();
    }
}

function initPhoneInputSync() {
    const fullPhone = document.getElementById("phone");
    if (fullPhone && fullPhone.value && fullPhone.value.includes("-")) {
        const parts = fullPhone.value.split("-");
        if (parts.length === 3) {
            const p1 = document.getElementById("phone1");
            const p2 = document.getElementById("phone2");
            const p3 = document.getElementById("phone3");
            if (p1) p1.value = parts[0];
            if (p2) p2.value = parts[1];
            if (p3) p3.value = parts[2];
        }
    }
}

// DOM 로드 완료 시 기본 초기화
document.addEventListener('DOMContentLoaded', () => {
    const goodsSelect = document.getElementById('goodsId');
    if (goodsSelect) {
        onGoodsSelectChange(goodsSelect);
    }
    initThumbSliderDrag();
    initPhoneInputSync();
});
