/**
 * ==============================================================================
 * RoomTherapy Seller Goods Form Manager
 * ==============================================================================
 * [용도 및 쓰이는 곳]
 * - 판매자 굿즈 등록 화면: /seller/goods/new (templates/seller/goods-new.html)
 * - 판매자 굿즈 수정 화면: /seller/goods/{id}/edit (templates/seller/goods-edit.html)
 *
 * [핵심 기능 모듈]
 * 1. 인증코드 자동 생성 (generateRandomCodes)
 * 2. 굿즈 사진 매니저 (드래그 앤 드롭 순서 변경, 썸네일 자동 지정, 개별 삭제, DataTransfer 동기화)
 * 3. 스테이지 3D 오브제 매니저 (3D 모델 .glb/.gltf/fbx/obj/usdz 및 썸네일 등록/수정/삭제/순서 이동)
 * 4. 실시간 업로드 용량 계산 및 한도 초과(413 방지) 경고 인디케이터
 * 5. 비동기 XMLHttpRequest 파일 전송 & 실시간 프로그레스 모달 (네트워크 0~90% -> S3 병렬 저장 90~99%)
 * 6. 굿즈 삭제 중앙 다이얼로그 모달 제어
 * ==============================================================================
 */

/**
 * [모듈 1: 인증코드 랜덤 5개 자동 생성]
 * - 형식: RT-XXXXXX (영문 대문자 + 숫자)
 * @param {string} targetTextareaId - 코드가 채워질 textarea ID (기본: authCodesInput 또는 additionalAuthCodesInput)
 */
function generateRandomCodes(targetTextareaId) {
    const textarea = document.getElementById(targetTextareaId || 'authCodesInput')
        || document.getElementById('additionalAuthCodesInput');
    if (!textarea) return;
    const prefix = 'RT-';
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let newCodes = [];
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

/**
 * [모듈 2: 굿즈 삭제 확인 모달 열기/닫기]
 * - 쓰이는 곳: goods-edit.html 하단 [굿즈 삭제] 버튼
 */
function openGoodsDeleteModal() {
    const modal = document.getElementById('goodsDeleteModal');
    if (modal) modal.classList.add('active');
}

function closeGoodsDeleteModal() {
    const modal = document.getElementById('goodsDeleteModal');
    if (modal) modal.classList.remove('active');
}

function handleGoodsDeleteBackdrop(event) {
    if (event.target.id === 'goodsDeleteModal') {
        closeGoodsDeleteModal();
    }
}

// 전역 폼 컨트롤러 인스턴스 (인라인 onclick 함수와의 연결용)
let sellerGoodsFormInstance = null;

/**
 * [모듈 3: 굿즈 폼 전체 초기화 및 컨트롤러 바인딩]
 * @param {Object} options - 뷰 템플릿에서 주입하는 설정 파라미터
 *   - formId: 'goodsCreateForm' | 'goodsEditForm'
 *   - submitBtnLabel: '굿즈 등록 완료' | '굿즈 수정 완료'
 *   - initialUrls: 기존 이미지 URL 배열 (수정 화면용)
 *   - initialObjects3d: 기존 3D 오브제 객체 배열 (수정 화면용)
 *   - maxImageMb, maxObjectMb, maxTotalMb: 서버 단일 진실 공급원(UploadModelAdvice) 설정값
 */
function initSellerGoodsForm(options) {
    const config = Object.assign({
        formId: 'goodsCreateForm',
        submitBtnLabel: '굿즈 등록 완료',
        initialUrls: [],
        initialObjects3d: [],
        maxImageMb: 70,
        maxObjectMb: 300,
        maxTotalMb: 3000
    }, options);

    // 허용 3D 확장자 및 용량 상수
    const ALLOWED_3D_EXTS = ['.glb', '.gltf', '.fbx', '.obj', '.usdz'];
    const MAX_IMAGE_MB = config.maxImageMb;
    const MAX_OBJECT_FILE_MB = config.maxObjectMb;
    const MAX_TOTAL_UPLOAD_MB = config.maxTotalMb;

    const MAX_FILE_SIZE = MAX_IMAGE_MB * 1024 * 1024;
    const MAX_MODEL_FILE_SIZE = MAX_OBJECT_FILE_MB * 1024 * 1024;
    const MAX_TOTAL_REQUEST_SIZE = MAX_TOTAL_UPLOAD_MB * 1024 * 1024;

    // 내부 상태 변수
    let imageItems = []; // { type: 'existing'|'new', url?: string, file?: File, previewUrl: string }
    let draggedIndex = null;
    let object3dItems = []; // { id, name, description, modelUrl, thumbnailUrl, file, thumbFile, sizeError }
    let isSubmittingForm = false;

    // --- 1. 초기 굿즈 사진 데이터 로드 ---
    if (Array.isArray(config.initialUrls) && config.initialUrls.length > 0) {
        imageItems = config.initialUrls.map(url => ({
            type: 'existing',
            url: url,
            previewUrl: url
        }));
    } else {
        const rawUrlInput = document.getElementById('imageUrl');
        const rawUrlVal = rawUrlInput ? rawUrlInput.value : '';
        if (rawUrlVal && rawUrlVal.trim()) {
            imageItems = rawUrlVal.split(',')
                .map(s => s.trim())
                .filter(s => s && !s.startsWith('__NEW_FILE_'))
                .map(url => ({ type: 'existing', url: url, previewUrl: url }));
        }
    }

    // --- 2. 초기 3D 오브제 데이터 로드 ---
    if (Array.isArray(config.initialObjects3d) && config.initialObjects3d.length > 0) {
        object3dItems = config.initialObjects3d.map(obj => ({
            id: obj.id != null ? obj.id : null,
            name: obj.name || '',
            description: obj.description || '',
            modelUrl: obj.modelUrl || '',
            thumbnailUrl: obj.thumbnailUrl || '',
            file: null,
            thumbFile: null,
            existingModelBytes: null,
            existingThumbBytes: null,
            sizeError: ''
        }));
    }

    // --- 3. 실시간 용량 계산 유틸리티 ---
    function getImageUploadBytes() {
        let total = 0;
        imageItems.forEach(img => {
            if (img.type === 'new' && img.file) {
                total += img.file.size;
            } else if (img.type === 'existing' && typeof img.existingBytes === 'number') {
                total += img.existingBytes;
            }
        });
        return total;
    }

    function get3dModelUploadBytes(excludeIdx = -1) {
        let total = 0;
        object3dItems.forEach((obj, i) => {
            if (i !== excludeIdx) {
                if (obj.file) {
                    total += obj.file.size;
                } else if (obj.modelUrl && typeof obj.existingModelBytes === 'number') {
                    total += obj.existingModelBytes;
                }
            }
        });
        return total;
    }

    function get3dThumbUploadBytes() {
        let total = 0;
        object3dItems.forEach(obj => {
            if (obj.thumbFile) {
                total += obj.thumbFile.size;
            } else if (obj.thumbnailUrl && typeof obj.existingThumbBytes === 'number') {
                total += obj.existingThumbBytes;
            }
        });
        return total;
    }

    function get3dObjectUploadBytes(excludeIdx = -1) {
        return get3dModelUploadBytes(excludeIdx) + get3dThumbUploadBytes();
    }

    function getTotalSelectedBytes(excludeIdx = -1) {
        return getImageUploadBytes() + get3dObjectUploadBytes(excludeIdx);
    }

    function formatMb(bytes) {
        return (bytes / (1024 * 1024)).toFixed(1);
    }

    // 기존 등록된 URL의 파일 용량(bytes)을 HEAD 요청으로 가볍게 비동기 조회
    function fetchFileSize(url, callback) {
        if (!url || typeof url !== 'string' || !url.trim() || url.startsWith('data:')) return;
        try {
            fetch(url.trim(), { method: 'HEAD' })
                .then(res => {
                    if (!res.ok) return null;
                    const len = res.headers.get('content-length');
                    return len ? parseInt(len, 10) : null;
                })
                .then(bytes => {
                    if (typeof bytes === 'number' && !isNaN(bytes) && bytes > 0) {
                        callback(bytes);
                    }
                })
                .catch(() => {
                    // CORS 또는 네트워크 제한 시 조용히 무시 (폴백)
                });
        } catch (e) {
            // 예외 방어
        }
    }

    function loadInitialExistingFileSizes() {
        // 기존 사진들 HEAD 조회
        imageItems.forEach(img => {
            if (img.type === 'existing' && img.url && img.existingBytes === undefined) {
                fetchFileSize(img.url, (bytes) => {
                    img.existingBytes = bytes;
                    updateAllUploadSizeIndicators();
                });
            }
        });

        // 기존 3D 오브제 모델 및 썸네일 HEAD 조회
        object3dItems.forEach(obj => {
            if (obj.modelUrl && obj.existingModelBytes === null) {
                fetchFileSize(obj.modelUrl, (bytes) => {
                    obj.existingModelBytes = bytes;
                    updateAllUploadSizeIndicators();
                    render3dObjectsList();
                });
            }
            if (obj.thumbnailUrl && obj.existingThumbBytes === null) {
                fetchFileSize(obj.thumbnailUrl, (bytes) => {
                    obj.existingThumbBytes = bytes;
                    updateAllUploadSizeIndicators();
                });
            }
        });
    }

    /**
     * 화면 상의 모든 용량 표시 뱃지 및 제출 버튼 활성화 상태 갱신
     */
    function updateAllUploadSizeIndicators() {
        const imageBytes = getImageUploadBytes();
        const modelBytes = get3dModelUploadBytes(-1);
        const thumbBytes = get3dThumbUploadBytes();
        const obj3dBytes = modelBytes + thumbBytes;
        const totalBytes = imageBytes + obj3dBytes;

        const isOverLimit = totalBytes > MAX_TOTAL_REQUEST_SIZE;

        // 굿즈 사진 섹션 뱃지
        const imageSizeBadge = document.getElementById('imageSizeBadge');
        if (imageSizeBadge) {
            imageSizeBadge.textContent = `${formatMb(imageBytes)} MB / ${MAX_TOTAL_UPLOAD_MB} MB`;
            imageSizeBadge.classList.toggle('warn', imageBytes > MAX_TOTAL_REQUEST_SIZE);
        }

        // 3D 오브제 섹션 뱃지
        const obj3dSizeBadge = document.getElementById('obj3dSizeBadge');
        if (obj3dSizeBadge) {
            obj3dSizeBadge.textContent = `${formatMb(obj3dBytes)} MB / ${MAX_TOTAL_UPLOAD_MB} MB`;
            obj3dSizeBadge.classList.toggle('warn', obj3dBytes > MAX_TOTAL_REQUEST_SIZE);
        }

        // 하단 총합 요약 박스
        const totalBadge = document.getElementById('totalUploadSizeBadge');
        if (totalBadge) {
            totalBadge.textContent = `${formatMb(totalBytes)} MB / ${MAX_TOTAL_UPLOAD_MB} MB`;
            totalBadge.classList.toggle('warn', isOverLimit);
        }

        const imgDetail = document.getElementById('summaryImageSize');
        if (imgDetail) imgDetail.textContent = `${formatMb(imageBytes)} MB`;
        const modelDetail = document.getElementById('summary3dModelSize');
        if (modelDetail) modelDetail.textContent = `${formatMb(modelBytes)} MB`;
        const thumbDetail = document.getElementById('summary3dThumbSize');
        if (thumbDetail) thumbDetail.textContent = `${formatMb(thumbBytes)} MB`;

        // 허용용량 초과 경고 문구 및 제출 버튼 상태 제어
        const limitNotice = document.getElementById('uploadOverLimitNotice');
        const submitBtn = document.getElementById('goodsSubmitBtn');

        if (limitNotice) {
            limitNotice.style.display = isOverLimit ? 'block' : 'none';
        }

        if (submitBtn && !isSubmittingForm) {
            if (isOverLimit) {
                submitBtn.disabled = true;
                submitBtn.classList.add('btn-over-limit');
                submitBtn.title = '파일 업로드 허용용량을 넘겼습니다.';
            } else {
                submitBtn.disabled = false;
                submitBtn.classList.remove('btn-over-limit');
                submitBtn.removeAttribute('title');
            }
        }
    }

    // --- 4. 굿즈 사진 DOM 렌더링 & 드래그 앤 드롭 ---
    function renderImageGrid() {
        const grid = document.getElementById('imageGrid');
        const badge = document.getElementById('imageCountBadge');
        if (!grid || !badge) return;

        grid.innerHTML = '';
        badge.textContent = `${imageItems.length}장`;

        if (imageItems.length === 0) {
            grid.innerHTML = '<div class="image-empty-notice">등록된 사진이 없습니다. 아래에서 사진을 추가해주세요.</div>';
            syncFormData();
            updateAllUploadSizeIndicators();
            return;
        }

        imageItems.forEach((item, index) => {
            const card = document.createElement('div');
            card.className = 'image-card';
            card.draggable = true;
            card.dataset.index = index;

            // 첫 번째 사진: 대표 사진 뱃지 부여
            if (index === 0) {
                const mainBadge = document.createElement('div');
                mainBadge.className = 'badge-main-photo';
                mainBadge.textContent = '★ 대표 사진';
                card.appendChild(mainBadge);
            }

            // 개별 삭제 버튼
            const delBtn = document.createElement('button');
            delBtn.type = 'button';
            delBtn.className = 'btn-delete-image';
            delBtn.title = '이 사진 삭제';
            delBtn.innerHTML = '&times;';
            delBtn.onclick = (e) => {
                e.stopPropagation();
                deleteImage(index);
            };
            card.appendChild(delBtn);

            // 이미지 미리보기
            const img = document.createElement('img');
            img.src = item.previewUrl;
            img.alt = '굿즈 사진 ' + (index + 1);
            card.appendChild(img);

            // 좌우 이동 컨트롤 버튼 (모바일 정밀 터치용)
            const controls = document.createElement('div');
            controls.className = 'card-order-controls';

            const leftBtn = document.createElement('button');
            leftBtn.type = 'button';
            leftBtn.className = 'btn-order-move';
            leftBtn.innerHTML = '&#9664;';
            leftBtn.title = '앞으로 이동';
            leftBtn.disabled = (index === 0);
            leftBtn.onclick = (e) => {
                e.stopPropagation();
                moveImage(index, index - 1);
            };

            const rightBtn = document.createElement('button');
            rightBtn.type = 'button';
            rightBtn.className = 'btn-order-move';
            rightBtn.innerHTML = '&#9654;';
            rightBtn.title = '뒤로 이동';
            rightBtn.disabled = (index === imageItems.length - 1);
            rightBtn.onclick = (e) => {
                e.stopPropagation();
                moveImage(index, index + 1);
            };

            controls.appendChild(leftBtn);
            controls.appendChild(rightBtn);
            card.appendChild(controls);

            // 드래그 앤 드롭 이벤트 등록
            card.addEventListener('dragstart', handleDragStart);
            card.addEventListener('dragenter', handleDragEnter);
            card.addEventListener('dragover', handleDragOver);
            card.addEventListener('dragleave', handleDragLeave);
            card.addEventListener('drop', handleDrop);
            card.addEventListener('dragend', handleDragEnd);

            grid.appendChild(card);
        });

        syncFormData();
        updateAllUploadSizeIndicators();
    }

    function handleDragStart(e) {
        draggedIndex = parseInt(this.dataset.index);
        this.classList.add('dragging');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', draggedIndex);
    }

    function handleDragEnter(e) {
        e.preventDefault();
        this.classList.add('drag-over');
    }

    function handleDragOver(e) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
    }

    function handleDragLeave() {
        this.classList.remove('drag-over');
    }

    function handleDrop(e) {
        e.preventDefault();
        this.classList.remove('drag-over');
        const targetIndex = parseInt(this.dataset.index);

        if (draggedIndex !== null && draggedIndex !== targetIndex) {
            const movedItem = imageItems.splice(draggedIndex, 1)[0];
            imageItems.splice(targetIndex, 0, movedItem);
            renderImageGrid();
        }
    }

    function handleDragEnd() {
        this.classList.remove('dragging');
        document.querySelectorAll('.image-card').forEach(c => c.classList.remove('drag-over'));
        draggedIndex = null;
    }

    function moveImage(fromIndex, toIndex) {
        if (toIndex < 0 || toIndex >= imageItems.length) return;
        const item = imageItems.splice(fromIndex, 1)[0];
        imageItems.splice(toIndex, 0, item);
        renderImageGrid();
    }

    function deleteImage(index) {
        const removed = imageItems.splice(index, 1)[0];
        if (removed && removed.type === 'new' && removed.previewUrl.startsWith('blob:')) {
            URL.revokeObjectURL(removed.previewUrl);
        }
        renderImageGrid();
    }

    function addFiles(fileList) {
        if (!fileList || fileList.length === 0) return;
        const errEl = document.getElementById('imageSizeErrorNotice');
        let errorMsg = '';
        for (let i = 0; i < fileList.length; i++) {
            const file = fileList[i];
            if (!file.type.startsWith('image/')) continue;
            const fileMb = (file.size / (1024 * 1024)).toFixed(1);
            const totalWithFile = getTotalSelectedBytes(-1) + file.size;
            const totalMb = (totalWithFile / (1024 * 1024)).toFixed(1);
            if (file.size > MAX_FILE_SIZE) {
                errorMsg = `⚠️ 용량이 큽니다 (${file.name}: ${fileMb}MB) — 사진별 최대용량: ${MAX_IMAGE_MB}MB / 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB`;
                continue;
            }
            if (totalWithFile > MAX_TOTAL_REQUEST_SIZE) {
                errorMsg = `⚠️ 용량이 큽니다 (전체 합산 ${totalMb}MB) — 사진별 최대용량: ${MAX_IMAGE_MB}MB / 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB`;
                continue;
            }
            imageItems.push({
                type: 'new',
                file: file,
                previewUrl: URL.createObjectURL(file)
            });
        }
        if (errEl) {
            if (errorMsg) {
                errEl.textContent = errorMsg;
                errEl.style.display = 'block';
            } else {
                errEl.textContent = '';
                errEl.style.display = 'none';
            }
        }
        renderImageGrid();
    }

    function addDirectUrl() {
        const input = document.getElementById('directUrlInput');
        if (!input) return;
        const url = input.value.trim();
        if (!url) return;
        imageItems.push({
            type: 'existing',
            url: url,
            previewUrl: url
        });
        input.value = '';
        renderImageGrid();
    }

    function syncFormData() {
        const dt = new DataTransfer();
        let newFileCount = 0;
        const newFileIndexMap = new Map();

        imageItems.forEach(item => {
            if (item.type === 'new' && item.file) {
                dt.items.add(item.file);
                newFileIndexMap.set(item, newFileCount++);
            }
        });

        const fileInput = document.getElementById('imageFiles');
        if (fileInput) {
            fileInput.files = dt.files;
        }

        const tokenList = imageItems.map(item => {
            if (item.type === 'existing') {
                return item.url;
            } else {
                const idx = newFileIndexMap.get(item);
                return `__NEW_FILE_${idx}__`;
            }
        });

        const hiddenUrl = document.getElementById('imageUrl');
        if (hiddenUrl) {
            hiddenUrl.value = tokenList.join(',');
        }
    }

    // 드롭존 및 파일인풋 이벤트 리스너 바인딩
    const dropzone = document.getElementById('uploadDropzone');
    const fileInput = document.getElementById('imageFiles');

    if (dropzone && fileInput) {
        dropzone.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', (e) => addFiles(e.target.files));

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('drag-active');
        });
        dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-active'));
        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('drag-active');
            if (e.dataTransfer && e.dataTransfer.files) {
                addFiles(e.dataTransfer.files);
            }
        });
    }

    // --- 5. 스테이지 3D 오브제 매니저 ---
    function extractModelExt(filenameOrUrl) {
        if (!filenameOrUrl) return '';
        const clean = filenameOrUrl.split('?')[0].trim().toLowerCase();
        const dotIdx = clean.lastIndexOf('.');
        return dotIdx >= 0 ? clean.substring(dotIdx) : '';
    }

    function add3dObjectItem() {
        object3dItems.push({
            id: null,
            name: '',
            description: '',
            modelUrl: '',
            thumbnailUrl: '',
            file: null,
            thumbFile: null,
            sizeError: ''
        });
        render3dObjectsList();
    }

    function move3dObjectItem(fromIdx, toIdx) {
        if (toIdx < 0 || toIdx >= object3dItems.length) return;
        const moved = object3dItems.splice(fromIdx, 1)[0];
        object3dItems.splice(toIdx, 0, moved);
        render3dObjectsList();
    }

    function delete3dObjectItem(index) {
        object3dItems.splice(index, 1);
        render3dObjectsList();
    }

    function render3dObjectsList() {
        const listEl = document.getElementById('obj3dList');
        const badgeEl = document.getElementById('obj3dCountBadge');
        if (!listEl || !badgeEl) return;

        listEl.innerHTML = '';
        badgeEl.textContent = `${object3dItems.length}개`;

        if (object3dItems.length === 0) {
            listEl.innerHTML = '<div class="image-empty-notice" style="background:#FFFFFF;border:1px dashed var(--border-light);border-radius:8px;">등록된 3D 오브제가 없습니다. 아래 <strong>[+ 3D 오브제 추가]</strong> 버튼을 눌러 스테이지 오브제를 등록할 수 있습니다.</div>';
            sync3dObjectsFormData();
            updateAllUploadSizeIndicators();
            return;
        }

        object3dItems.forEach((item, idx) => {
            const card = document.createElement('div');
            card.className = 'obj3d-card';

            const numStr = String(idx + 1).padStart(2, '0');
            const ext = item.file ? extractModelExt(item.file.name) : extractModelExt(item.modelUrl);
            const extLabel = ext ? ext.replace('.', '').toUpperCase() : '미선택';

            // 모델 용량 계산 (신규 파일 크기 우선, 없으면 기존 URL 크기)
            let modelBytes = null;
            if (item.file) {
                modelBytes = item.file.size;
            } else if (typeof item.existingModelBytes === 'number') {
                modelBytes = item.existingModelBytes;
            }
            const sizeLabel = modelBytes !== null ? `${formatMb(modelBytes)} MB` : '';

            // 헤더 (순번, 포맷 뱃지, 용량 뱃지, 이동 및 삭제 액션)
            const header = document.createElement('div');
            header.className = 'obj3d-card-header';

            const badgeGroup = document.createElement('div');
            badgeGroup.className = 'obj3d-badge-group';
            let badgeGroupHtml = `<span class="obj3d-index-badge">OBJECT ${numStr}</span><span class="obj3d-format-badge">${extLabel}</span>`;
            if (sizeLabel) {
                badgeGroupHtml += `<span class="obj3d-size-badge">${sizeLabel}</span>`;
            }
            badgeGroup.innerHTML = badgeGroupHtml;

            const actions = document.createElement('div');
            actions.className = 'obj3d-actions';

            const upBtn = document.createElement('button');
            upBtn.type = 'button';
            upBtn.className = 'btn-obj3d-ctrl';
            upBtn.textContent = '▲';
            upBtn.title = '위로 이동';
            upBtn.disabled = (idx === 0);
            upBtn.onclick = () => move3dObjectItem(idx, idx - 1);

            const downBtn = document.createElement('button');
            downBtn.type = 'button';
            downBtn.className = 'btn-obj3d-ctrl';
            downBtn.textContent = '▼';
            downBtn.title = '아래로 이동';
            downBtn.disabled = (idx === object3dItems.length - 1);
            downBtn.onclick = () => move3dObjectItem(idx, idx + 1);

            const delBtn = document.createElement('button');
            delBtn.type = 'button';
            delBtn.className = 'btn-obj3d-delete';
            delBtn.textContent = '삭제';
            delBtn.onclick = () => delete3dObjectItem(idx);

            actions.appendChild(upBtn);
            actions.appendChild(downBtn);
            actions.appendChild(delBtn);

            header.appendChild(badgeGroup);
            header.appendChild(actions);
            card.appendChild(header);

            // 1. 오브제 이름
            const nameRow = document.createElement('div');
            nameRow.className = 'obj3d-field-row';
            const nameLabel = document.createElement('label');
            nameLabel.className = 'obj3d-sub-label';
            nameLabel.innerHTML = '1. 오브제 이름 <span style="color:var(--red);">*</span>';
            const nameInput = document.createElement('input');
            nameInput.type = 'text';
            nameInput.className = 'input-text';
            nameInput.placeholder = '예: 따뜻한 원목 협탁 / 아늑한 무드등';
            nameInput.value = item.name || '';
            nameInput.addEventListener('input', (e) => {
                item.name = e.target.value;
                sync3dObjectsFormData();
            });
            nameRow.appendChild(nameLabel);
            nameRow.appendChild(nameInput);
            card.appendChild(nameRow);

            // 2. 오브제 설명
            const descRow = document.createElement('div');
            descRow.className = 'obj3d-field-row';
            const descLabel = document.createElement('label');
            descLabel.className = 'obj3d-sub-label';
            descLabel.textContent = '2. 오브제 설명';
            const descInput = document.createElement('textarea');
            descInput.className = 'input-text';
            descInput.style.minHeight = '52px';
            descInput.placeholder = '전시관 상세 뷰에서 오브제와 함께 표시될 설명을 입력하세요.';
            descInput.value = item.description || '';
            descInput.addEventListener('input', (e) => {
                item.description = e.target.value;
                sync3dObjectsFormData();
            });
            descRow.appendChild(descLabel);
            descRow.appendChild(descInput);
            card.appendChild(descRow);

            // 3. 오브제 3D 모델 파일 또는 URL
            const modelRow = document.createElement('div');
            modelRow.className = 'obj3d-field-row';
            const modelLabel = document.createElement('label');
            modelLabel.className = 'obj3d-sub-label';
            modelLabel.innerHTML = `3. 오브제 3D 모델 파일 (.glb, .gltf, .fbx, .obj, .usdz / <span style="color:var(--gray);font-weight:600;">오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB, 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB</span>) <span style="color:var(--red);">*</span>`;

            const fileWrap = document.createElement('div');
            fileWrap.className = 'obj3d-file-row';

            const singleFileInput = document.createElement('input');
            singleFileInput.type = 'file';
            singleFileInput.accept = '.glb,.gltf,.fbx,.obj,.usdz';
            singleFileInput.style.display = 'none';

            const fileBtn = document.createElement('button');
            fileBtn.type = 'button';
            fileBtn.className = 'btn-obj3d-file';
            fileBtn.textContent = item.file || item.modelUrl ? '📂 3D 파일 변경' : '📂 3D 파일 선택';
            fileBtn.onclick = () => singleFileInput.click();

            const fileStatus = document.createElement('span');
            fileStatus.className = 'obj3d-file-status';
            if (item.sizeError) {
                fileStatus.textContent = item.sizeError;
                fileStatus.style.color = 'var(--red)';
                fileStatus.style.fontWeight = '800';
            } else if (item.file) {
                fileStatus.textContent = `새 파일 선택됨: ${item.file.name} (${(item.file.size / (1024 * 1024)).toFixed(2)}MB) — 오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB / 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB`;
                fileStatus.style.color = '#16A34A';
            } else if (item.modelUrl) {
                fileStatus.textContent = `현재 모델: ${item.modelUrl} (오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB, 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB)`;
            } else {
                fileStatus.textContent = `선택된 3D 모델 파일 없음 (오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB, 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB)`;
                fileStatus.style.color = 'var(--gray)';
            }

            singleFileInput.addEventListener('change', (e) => {
                if (!e.target.files || e.target.files.length === 0) return;
                const chosen = e.target.files[0];
                const chosenExt = extractModelExt(chosen.name);
                if (!ALLOWED_3D_EXTS.includes(chosenExt)) {
                    item.sizeError = `⚠️ 지원하지 않는 확장자입니다 (${chosenExt || '없음'}) — .glb, .gltf, .fbx, .obj, .usdz만 가능`;
                    render3dObjectsList();
                    return;
                }
                const chosenMb = (chosen.size / (1024 * 1024)).toFixed(1);
                const totalWithChosen = getTotalSelectedBytes(idx) + chosen.size;
                const totalMb = (totalWithChosen / (1024 * 1024)).toFixed(1);

                if (chosen.size > MAX_MODEL_FILE_SIZE) {
                    item.file = null;
                    item.sizeError = `⚠️ 용량이 큽니다 (${chosenMb}MB) — 오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB / 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB`;
                    render3dObjectsList();
                    return;
                }
                if (totalWithChosen > MAX_TOTAL_REQUEST_SIZE) {
                    item.file = null;
                    item.sizeError = `⚠️ 용량이 큽니다 (전체 합산 ${totalMb}MB) — 오브제별 최대용량: ${MAX_OBJECT_FILE_MB}MB / 총 허용용량: ${MAX_TOTAL_UPLOAD_MB}MB`;
                    render3dObjectsList();
                    return;
                }
                item.sizeError = '';
                item.file = chosen;
                item.existingModelBytes = null;
                render3dObjectsList();
            });

            fileWrap.appendChild(fileBtn);
            fileWrap.appendChild(fileStatus);
            fileWrap.appendChild(singleFileInput);
            modelRow.appendChild(modelLabel);
            modelRow.appendChild(fileWrap);

            const urlInput = document.createElement('input');
            urlInput.type = 'text';
            urlInput.className = 'input-text';
            urlInput.style.marginTop = '6px';
            urlInput.style.height = '34px';
            urlInput.style.fontSize = '11.5px';
            urlInput.placeholder = '또는 외부/기존 3D 모델 URL 직접 입력·수정 (예: /uploads/goods-3d/sample.glb)';
            urlInput.value = item.file ? '' : (item.modelUrl || '');
            urlInput.addEventListener('input', (e) => {
                item.modelUrl = e.target.value.trim();
                item.existingModelBytes = null;
                if (item.modelUrl) {
                    item.file = null;
                    fetchFileSize(item.modelUrl, (bytes) => {
                        item.existingModelBytes = bytes;
                        updateAllUploadSizeIndicators();
                        render3dObjectsList();
                    });
                }
                sync3dObjectsFormData();
            });

            // 4. 오브제 썸네일
            const thumbRow = document.createElement('div');
            thumbRow.className = 'obj3d-field-row';
            const thumbLabel = document.createElement('label');
            thumbLabel.className = 'obj3d-sub-label';
            thumbLabel.innerHTML = '4. 오브제 썸네일 이미지 (전시관 카드 썸네일용 / 선택)';

            const thumbWrap = document.createElement('div');
            thumbWrap.className = 'obj3d-file-row';

            const singleThumbInput = document.createElement('input');
            singleThumbInput.type = 'file';
            singleThumbInput.accept = 'image/*';
            singleThumbInput.style.display = 'none';

            const thumbBtn = document.createElement('button');
            thumbBtn.type = 'button';
            thumbBtn.className = 'btn-obj3d-file';
            thumbBtn.textContent = item.thumbFile || item.thumbnailUrl ? '🖼️ 썸네일 변경' : '🖼️ 썸네일 이미지 선택';
            thumbBtn.onclick = () => singleThumbInput.click();

            const thumbStatus = document.createElement('span');
            thumbStatus.className = 'obj3d-file-status';
            if (item.thumbFile) {
                const mb = (item.thumbFile.size / (1024 * 1024)).toFixed(2);
                thumbStatus.textContent = `새 파일 선택됨: ${item.thumbFile.name} (${mb}MB)`;
                thumbStatus.style.color = '#15803D';
            } else if (item.thumbnailUrl) {
                thumbStatus.textContent = `현재 썸네일: ${item.thumbnailUrl}`;
                thumbStatus.style.color = 'var(--text-light)';
            } else {
                thumbStatus.textContent = '선택된 이미지 없음';
                thumbStatus.style.color = 'var(--gray)';
            }

            singleThumbInput.addEventListener('change', (e) => {
                const chosen = e.target.files && e.target.files[0];
                if (!chosen) return;
                item.thumbFile = chosen;
                render3dObjectsList();
            });

            thumbWrap.appendChild(thumbBtn);
            thumbWrap.appendChild(thumbStatus);
            thumbWrap.appendChild(singleThumbInput);
            thumbRow.appendChild(thumbLabel);
            thumbRow.appendChild(thumbWrap);

            const thumbUrlInput = document.createElement('input');
            thumbUrlInput.type = 'text';
            thumbUrlInput.className = 'input-text';
            thumbUrlInput.style.marginTop = '6px';
            thumbUrlInput.style.height = '34px';
            thumbUrlInput.style.fontSize = '11.5px';
            thumbUrlInput.placeholder = '또는 썸네일 이미지 URL 직접 입력·수정 (선택)';
            thumbUrlInput.value = item.thumbFile ? '' : (item.thumbnailUrl || '');
            thumbUrlInput.addEventListener('input', (e) => {
                item.thumbnailUrl = e.target.value.trim();
                if (item.thumbnailUrl) {
                    item.thumbFile = null;
                }
                sync3dObjectsFormData();
            });
            thumbRow.appendChild(thumbUrlInput);

            card.appendChild(modelRow);
            card.appendChild(thumbRow);
            listEl.appendChild(card);
        });

        sync3dObjectsFormData();
        updateAllUploadSizeIndicators();
    }

    function sync3dObjectsFormData() {
        const hiddenContainer = document.getElementById('objects3dHiddenInputs');
        const modelFilesInput = document.getElementById('objectModelFiles');
        const thumbFilesInput = document.getElementById('objectThumbnailFiles');
        if (!hiddenContainer || !modelFilesInput) return;

        hiddenContainer.innerHTML = '';
        const dtModel = new DataTransfer();
        const dtThumb = new DataTransfer();
        let modelFileIdx = 0;
        let thumbFileIdx = 0;

        const submittedInput = document.createElement('input');
        submittedInput.type = 'hidden';
        submittedInput.name = 'objects3dSubmitted';
        submittedInput.value = 'true';
        hiddenContainer.appendChild(submittedInput);

        object3dItems.forEach((item) => {
            let resolvedModelToken = item.modelUrl || '';
            if (item.file) {
                dtModel.items.add(item.file);
                resolvedModelToken = `__NEW_MODEL_${modelFileIdx++}__`;
            }

            let resolvedThumbToken = item.thumbnailUrl || '';
            if (item.thumbFile) {
                dtThumb.items.add(item.thumbFile);
                resolvedThumbToken = `__NEW_THUMB_${thumbFileIdx++}__`;
            }

            const idHidden = document.createElement('input');
            idHidden.type = 'hidden';
            idHidden.name = 'objectIds';
            idHidden.value = item.id != null ? String(item.id) : '';
            hiddenContainer.appendChild(idHidden);

            const nameHidden = document.createElement('input');
            nameHidden.type = 'hidden';
            nameHidden.name = 'objectNames';
            nameHidden.value = item.name || '';
            hiddenContainer.appendChild(nameHidden);

            const descHidden = document.createElement('input');
            descHidden.type = 'hidden';
            descHidden.name = 'objectDescriptions';
            descHidden.value = item.description || '';
            hiddenContainer.appendChild(descHidden);

            const urlHidden = document.createElement('input');
            urlHidden.type = 'hidden';
            urlHidden.name = 'objectModelUrls';
            urlHidden.value = resolvedModelToken;
            hiddenContainer.appendChild(urlHidden);

            const thumbHidden = document.createElement('input');
            thumbHidden.type = 'hidden';
            thumbHidden.name = 'objectThumbnailUrls';
            thumbHidden.value = resolvedThumbToken;
            hiddenContainer.appendChild(thumbHidden);
        });

        modelFilesInput.files = dtModel.files;
        if (thumbFilesInput) {
            thumbFilesInput.files = dtThumb.files;
        }
    }

    // --- 6. 폼 검증 및 비동기 XMLHttpRequest 업로드 전송 ---
    const formEl = document.getElementById(config.formId);
    if (formEl) {
        formEl.addEventListener('submit', function(e) {
            if (isSubmittingForm) {
                e.preventDefault();
                return false;
            }
            syncFormData();
            sync3dObjectsFormData();

            // 유효성 검사 1: 사진 1장 이상 필수
            if (imageItems.length === 0) {
                e.preventDefault();
                alert('굿즈 사진은 최소 1장 이상 등록되어야 합니다.');
                const dropzoneEl = document.getElementById('uploadDropzone');
                if (dropzoneEl) {
                    dropzoneEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                return false;
            }

            // 유효성 검사 2: 추가된 3D 오브제의 이름 및 모델 누락 여부 확인
            for (let i = 0; i < object3dItems.length; i++) {
                const obj = object3dItems[i];
                const hasName = obj.name && obj.name.trim().length > 0;
                const hasModel = (obj.file != null) || (obj.modelUrl && obj.modelUrl.trim().length > 0);
                if (!hasName && !hasModel) continue;
                if (!hasName) {
                    e.preventDefault();
                    alert(`OBJECT ${String(i + 1).padStart(2, '0')}의 오브제 이름을 입력해주세요.`);
                    return false;
                }
                if (!hasModel) {
                    e.preventDefault();
                    alert(`OBJECT ${String(i + 1).padStart(2, '0')} (${obj.name})의 3D 모델 파일을 선택하거나 URL을 입력해주세요.`);
                    return false;
                }
            }

            // 유효성 검사 3: 총 업로드 용량 한도 체크
            if (getTotalSelectedBytes(-1) > MAX_TOTAL_REQUEST_SIZE) {
                e.preventDefault();
                alert('파일 업로드 허용용량을 넘겼습니다.');
                return false;
            }

            e.preventDefault();
            submitFormWithProgress(formEl, config.submitBtnLabel);
        });
    }

    /**
     * XMLHttpRequest로 multipart 폼을 전송하고 실시간 진행률 게이지를 업데이트
     */
    function submitFormWithProgress(form, defaultBtnLabel) {
        isSubmittingForm = true;
        const submitBtn = document.getElementById('goodsSubmitBtn');
        const overlay = document.getElementById('uploadProgressOverlay');
        const iconEl = document.getElementById('uploadProgressIcon');
        const titleEl = document.getElementById('uploadProgressTitle');
        const subEl = document.getElementById('uploadProgressSub');
        const percentEl = document.getElementById('uploadProgressPercent');
        const bytesEl = document.getElementById('uploadProgressBytes');
        const fillEl = document.getElementById('uploadProgressFill');

        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = '⏳ 업로드 및 저장 중...';
        }

        const totalBytes = getTotalSelectedBytes(-1);
        const totalMbStr = (totalBytes / (1024 * 1024)).toFixed(1);

        if (overlay) {
            iconEl.textContent = '🚀';
            titleEl.textContent = '3D 오브제 및 사진을 서버로 전송하고 있습니다...';
            subEl.textContent = totalBytes > 0
                ? '파일 용량에 따라 수 초~수십 초가 소요될 수 있습니다.'
                : '굿즈 정보를 서버에 반영하고 있습니다.';
            percentEl.textContent = '0%';
            bytesEl.textContent = totalBytes > 0 ? `0.0 MB / ${totalMbStr} MB` : '데이터 전송 중';
            fillEl.style.width = '0%';
            overlay.classList.add('active');
        }

        let s3StageTimer = null;
        let currentPct = 0;

        function stopS3Timer() {
            if (s3StageTimer) {
                clearInterval(s3StageTimer);
                s3StageTimer = null;
            }
        }

        function resetSubmitState() {
            stopS3Timer();
            isSubmittingForm = false;
            if (overlay) overlay.classList.remove('active');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = defaultBtnLabel;
            }
        }

        const xhr = new XMLHttpRequest();
        xhr.open('POST', form.action, true);

        // 네트워크 전송 진행 이벤트 (0% ~ 90%)
        xhr.upload.addEventListener('progress', function(evt) {
            if (evt.lengthComputable && evt.total > 0) {
                const ratio = Math.min(1, evt.loaded / evt.total);
                currentPct = Math.min(90, Math.round(ratio * 90));
                const loadedMb = (evt.loaded / (1024 * 1024)).toFixed(1);
                const totalMb = (evt.total / (1024 * 1024)).toFixed(1);
                if (percentEl) percentEl.textContent = `${currentPct}%`;
                if (bytesEl) bytesEl.textContent = `${loadedMb} MB / ${totalMb} MB 전송됨`;
                if (fillEl) fillEl.style.width = `${currentPct}%`;
            }
        });

        // 서버 도착 후 S3 병렬 업로드 단계 (92% ~ 99%)
        xhr.upload.addEventListener('load', function() {
            currentPct = Math.max(currentPct, 92);
            if (iconEl) iconEl.textContent = '☁️';
            if (titleEl) titleEl.textContent = '클라우드 스토리지(S3)에 병렬 저장하고 있습니다...';
            if (subEl) subEl.textContent = '서버 전송이 완료되었으며 S3 버킷에 3D 모델 및 사진을 등록 중입니다.';
            if (percentEl) percentEl.textContent = `${currentPct}%`;
            if (bytesEl) bytesEl.textContent = 'S3 클라우드 저장 중...';
            if (fillEl) fillEl.style.width = `${currentPct}%`;

            let elapsedSec = 0;
            stopS3Timer();
            s3StageTimer = setInterval(function() {
                elapsedSec++;
                if (currentPct < 99) {
                    currentPct++;
                    if (percentEl) percentEl.textContent = `${currentPct}%`;
                    if (fillEl) fillEl.style.width = `${currentPct}%`;
                }
                if (bytesEl) {
                    bytesEl.textContent = `S3 클라우드 병렬 저장 중 (${elapsedSec}초 경과)`;
                }
            }, 700);
        });

        // 응답 완료 핸들러
        xhr.onload = function() {
            stopS3Timer();
            if (xhr.status >= 200 && xhr.status < 300) {
                let finalPath = '';
                try {
                    finalPath = xhr.responseURL ? new URL(xhr.responseURL, window.location.origin).pathname : '';
                } catch (err) {
                    finalPath = '';
                }

                // 검증 오류 등으로 다시 폼 화면이 응답된 경우
                if (finalPath && finalPath.indexOf('/seller/goods') === 0) {
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(xhr.responseText, 'text/html');
                    const alertErr = doc.querySelector('.alert-error');
                    const fieldErr = doc.querySelector('.field-error');
                    const errMsg = (alertErr && alertErr.textContent.trim())
                        || (fieldErr && fieldErr.textContent.trim())
                        || '입력값을 다시 확인해주세요.';
                    resetSubmitState();
                    alert('⚠️ ' + errMsg);
                    return;
                }

                if (iconEl) iconEl.textContent = '✅';
                if (titleEl) titleEl.textContent = '업로드 및 완료!';
                if (subEl) subEl.textContent = '굿즈 목록 페이지로 이동합니다...';
                if (percentEl) percentEl.textContent = '100%';
                if (bytesEl) bytesEl.textContent = '완료';
                if (fillEl) fillEl.style.width = '100%';

                window.location.href = xhr.responseURL || '/goods';
            } else if (xhr.status === 413) {
                resetSubmitState();
                alert('⚠️ 업로드 파일 용량이 서버 허용 한도를 초과했습니다 (413 Payload Too Large).');
            } else {
                resetSubmitState();
                alert(`⚠️ 업로드 중 서버 오류가 발생했습니다 (HTTP ${xhr.status}). 잠시 후 다시 시도해주세요.`);
            }
        };

        xhr.onerror = function() {
            resetSubmitState();
            alert('⚠️ 네트워크 연결 오류로 업로드에 실패했습니다. 인터넷 연결 또는 파일 용량을 확인해주세요.');
        };

        xhr.send(new FormData(form));
    }

    // 초기 렌더링
    renderImageGrid();
    render3dObjectsList();

    // 기존 등록된 파일들의 크기 비동기(HEAD) 감지 및 실시간 합산
    loadInitialExistingFileSizes();

    sellerGoodsFormInstance = {
        addDirectUrl,
        add3dObjectItem,
        move3dObjectItem,
        delete3dObjectItem,
        deleteImage,
        moveImage
    };

    return sellerGoodsFormInstance;
}

// 글로벌 래퍼 함수 (인라인 onclick 태그 호환용)
function addDirectUrl() {
    if (sellerGoodsFormInstance) sellerGoodsFormInstance.addDirectUrl();
}

function add3dObjectItem() {
    if (sellerGoodsFormInstance) sellerGoodsFormInstance.add3dObjectItem();
}
