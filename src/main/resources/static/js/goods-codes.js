/**
 * ============================================================================
 * goods-codes.js - 판매자 굿즈별 인증코드 관리 자바스크립트
 * ============================================================================
 *
 * [적용 화면]
 * - 판매자 인증코드 관리: /seller/goods/codes, /seller/goods/{id}/codes (src/main/resources/templates/seller/goods-codes.html)
 *
 * [주요 기능]
 * 1. 뷰 모드 전환 (switchCodeView: 'grouped' 굿즈별 모아보기 vs 'list' 통합 목록)
 * 2. 랜덤 인증코드 자동 생성 (generateRandomCodesForInput: 'RT-' 접두어 + 영숫자 6자리 5개 생성)
 * 3. 인증코드 클립보드 복사 (copyCodeText: 복사 성공 시 '✓ 복사됨' 시각 피드백)
 * 4. 인증코드 삭제 재확인 대화상자 (confirmDeleteCode)
 * 5. 통합 목록 실시간 키워드 검색 필터링 (filterCodesByKeyword)
 * ============================================================================
 */

/* 1. 뷰 모드 전환 ('grouped' vs 'list') */
function switchCodeView(mode) {
    const groupedEl = document.getElementById('viewGroupedContainer');
    const listEl = document.getElementById('viewListContainer');
    const btnGrouped = document.getElementById('tabBtnGrouped');
    const btnList = document.getElementById('tabBtnList');
    if (!groupedEl || !listEl) return;

    if (mode === 'list') {
        groupedEl.style.display = 'none';
        listEl.style.display = 'block';
        if (btnGrouped) btnGrouped.classList.remove('active');
        if (btnList) btnList.classList.add('active');
    } else {
        groupedEl.style.display = 'block';
        listEl.style.display = 'none';
        if (btnGrouped) btnGrouped.classList.add('active');
        if (btnList) btnList.classList.remove('active');
    }
}

/* 2. 새 인증코드 입력란에 5개의 랜덤 예시 코드 자동 생성 및 추가 */
function generateRandomCodesForInput(textareaId) {
    const textarea = document.getElementById(textareaId);
    if (!textarea) return;
    const prefix = 'RT-';
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const newCodes = [];
    for (let i = 0; i < 5; i++) {
        let code = prefix;
        for (let j = 0; j < 6; j++) {
            code += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        newCodes.push(code);
    }
    if (textarea.value.trim().length > 0) {
        textarea.value = textarea.value.trim() + '\n' + newCodes.join('\n');
    } else {
        textarea.value = newCodes.join('\n');
    }
}

/* 3. 인증코드 클립보드 복사 & 시각 피드백 */
function copyCodeText(btnEl, codeText) {
    if (!navigator.clipboard) {
        return;
    }
    navigator.clipboard.writeText(codeText).then(() => {
        const orig = btnEl.textContent;
        btnEl.textContent = '✓ 복사됨';
        btnEl.style.color = '#16A34A';
        setTimeout(() => {
            btnEl.textContent = orig;
            btnEl.style.color = '';
        }, 1200);
    });
}

/* 4. 인증코드 삭제 확인 대화상자 */
function confirmDeleteCode(goodsName, code) {
    return confirm(`[${goodsName}]\n인증코드 '${code}'을(를) 삭제하시겠습니까?`);
}

/* 5. 통합 목록 검색창 실시간 키워드 필터링 (코드 또는 상품명) */
function filterCodesByKeyword(keyword) {
    const q = (keyword || '').trim().toLowerCase();
    const rows = document.querySelectorAll('.searchable-code-row');
    rows.forEach(row => {
        const code = (row.getAttribute('data-code') || '').toLowerCase();
        const goods = (row.getAttribute('data-goods') || '').toLowerCase();
        if (!q || code.includes(q) || goods.includes(q)) {
            row.style.display = 'flex';
        } else {
            row.style.display = 'none';
        }
    });
}
