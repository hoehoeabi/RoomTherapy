/**
 * ============================================================================
 * deliveries.js - 주문 배송 관리 공통 자바스크립트
 * ============================================================================
 *
 * [적용 화면]
 * - 관리자 배송 신청 목록 관리: /admin/deliveries (src/main/resources/templates/admin/deliveries.html)
 * - 판매자 배송 주문 관리: /seller/deliveries (src/main/resources/templates/seller/deliveries.html)
 *
 * [주요 기능]
 * 1. 엑셀 다운로드 모달 제어 (openExportModal, closeExportModal)
 * 2. 엑셀 일괄 등록 모달 제어 (openImportModal, closeImportModal)
 * 3. 엑셀 추출 컬럼 전체 선택/해제 (toggleAllColumns)
 * 4. 모달 배경 클릭 시 닫기 (handleBackdropClick)
 * 5. Stored DOM-XSS 방어용 HTML 특수문자 이스케이프 (escapeHtml)
 * 6. 배송 주문 삭제 확인 커스텀 모달 제어 (confirmOrderDelete, closeDeleteConfirmModal, handleDeleteModalBackdrop)
 * ============================================================================
 */

/* 1. 엑셀 다운로드 모달 제어 */
function openExportModal() {
    const modal = document.getElementById('exportModal');
    if (modal) modal.classList.add('active');
}

function closeExportModal() {
    const modal = document.getElementById('exportModal');
    if (modal) modal.classList.remove('active');
}

/* 2. 엑셀 일괄 등록 모달 제어 */
function openImportModal() {
    const modal = document.getElementById('importModal');
    if (modal) modal.classList.add('active');
}

function closeImportModal() {
    const modal = document.getElementById('importModal');
    if (modal) modal.classList.remove('active');
}

/* 3. 모달 배경 클릭 시 닫기 */
function handleBackdropClick(event, modalId) {
    if (event.target.id === modalId) {
        document.getElementById(modalId).classList.remove('active');
    }
}

/* 4. 엑셀 컬럼 전체 선택 / 해제 */
function toggleAllColumns(master) {
    const checks = document.querySelectorAll('.col-check');
    checks.forEach(c => c.checked = master.checked);
}

/* 5. Stored DOM-XSS 방지용 HTML 엔티티 이스케이프 */
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/* 6. 주문 삭제 확인 모달 (역할별 액션 URL 대응: deleteUrlPrefix 파라미터 옵션 지원) */
function confirmOrderDelete(orderId, status, goodsName, customUrlPrefix) {
    const form = document.getElementById('deleteOrderForm');
    // 기본값은 현재 페이지 URL 경로에 따라 /admin/deliveries 또는 /seller/deliveries 자동 판별
    const prefix = customUrlPrefix || (window.location.pathname.startsWith('/admin') ? '/admin/deliveries' : '/seller/deliveries');
    form.action = prefix + '/' + encodeURIComponent(orderId) + '/delete';

    const iconEl = document.getElementById('deleteModalIcon');
    const titleEl = document.getElementById('deleteModalTitle');
    const subEl = document.getElementById('deleteModalSub');
    const descEl = document.getElementById('deleteModalDesc');

    const safeOrderId = escapeHtml(orderId);
    const safeGoodsName = escapeHtml(goodsName || '굿즈');
    const safeStatus = escapeHtml(status);

    if (status !== 'DELIVERED') {
        iconEl.textContent = '⚠️';
        titleEl.textContent = '아직 배송완료되지 않았습니다';
        subEl.textContent = '삭제하시겠습니까?';
        subEl.style.display = 'block';
        descEl.innerHTML = '주문번호 <strong>#' + safeOrderId + '</strong> (' + safeGoodsName + ')<br>현재 상태: <strong>' + safeStatus + '</strong>';
    } else {
        iconEl.textContent = '🗑️';
        titleEl.textContent = '배송 주문을 삭제하시겠습니까?';
        subEl.style.display = 'none';
        descEl.innerHTML = '주문번호 <strong>#' + safeOrderId + '</strong> (' + safeGoodsName + ')<br>삭제된 주문 정보는 복구할 수 없습니다.';
    }

    const modal = document.getElementById('deleteConfirmModal');
    if (modal) modal.classList.add('active');
}

function closeDeleteConfirmModal() {
    const modal = document.getElementById('deleteConfirmModal');
    if (modal) modal.classList.remove('active');
}

function handleDeleteModalBackdrop(event) {
    if (event.target.id === 'deleteConfirmModal') {
        closeDeleteConfirmModal();
    }
}

/* 7. 사용자 피드백 열람 모달 제어 */
function openFeedbackModal(orderId, applicant, goodsName, feedback) {
    const metaEl = document.getElementById('feedbackModalMeta');
    const contentEl = document.getElementById('feedbackModalContent');

    const safeOrderId = escapeHtml(orderId);
    const safeApplicant = escapeHtml(applicant || '익명');
    const safeGoodsName = escapeHtml(goodsName || '굿즈');

    if (metaEl) {
        metaEl.innerHTML = '주문번호 <strong>#' + safeOrderId + '</strong> · 신청자: <strong>' + safeApplicant + '</strong><br>' + safeGoodsName;
    }
    if (contentEl) {
        contentEl.textContent = feedback || '';
    }

    const modal = document.getElementById('feedbackModal');
    if (modal) modal.classList.add('active');
}

function closeFeedbackModal() {
    const modal = document.getElementById('feedbackModal');
    if (modal) modal.classList.remove('active');
}

function handleFeedbackModalBackdrop(event) {
    if (event.target.id === 'feedbackModal') {
        closeFeedbackModal();
    }
}

