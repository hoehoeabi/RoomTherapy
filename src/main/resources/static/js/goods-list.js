/**
 * ==============================================================================
 * RoomTherapy Goods List & Lightbox & Import Manager
 * ==============================================================================
 * [용도 및 쓰이는 곳]
 * - 굿즈 리워드 둘러보기 화면: /goods (templates/goods/list.html)
 *
 * [핵심 기능 모듈]
 * 1. 애플 리퀴드 글라스(Liquid Glass) 굿즈 이미지 상세보기 라이트박스 제어
 * 2. PC/모바일 가로 썸네일 슬라이더 드래그 & 터치 스와이프 제어
 * 3. 굿즈 삭제 확인 중앙 다이얼로그 모달 제어
 * 4. 엑셀/ZIP 대량 굿즈 일괄 등록 드롭존 및 70MB 파일 용량 검증
 * ==============================================================================
 */

        // 라이트박스 상태 관리
        let lightboxImages = [];
        let lightboxCurrentIndex = 0;

        function openGoodsImageLightbox(goodsName, images, initialIndex) {
            if (!images || images.length === 0) return;

            lightboxImages = images;
            lightboxCurrentIndex = Math.max(0, Math.min(initialIndex || 0, images.length - 1));

            const modal = document.getElementById('goodsImageLightbox');
            const titleEl = document.getElementById('lightboxTitle');
            const trackEl = document.getElementById('lightboxTrack');
            const dotsEl = document.getElementById('lightboxDots');

            titleEl.textContent = goodsName || '굿즈 이미지 상세보기';

            // 슬라이드 트랙 렌더링
            trackEl.innerHTML = '';
            images.forEach((src, idx) => {
                const slide = document.createElement('div');
                slide.className = 'lightbox-slide';
                const img = document.createElement('img');
                img.src = src;
                img.alt = (goodsName || '굿즈') + ' 사진 ' + (idx + 1);
                slide.appendChild(img);
                trackEl.appendChild(slide);
            });

            // 하단 인디케이터 점(Dot) 렌더링
            dotsEl.innerHTML = '';
            if (images.length > 1) {
                images.forEach((_, idx) => {
                    const dot = document.createElement('button');
                    dot.type = 'button';
                    dot.className = 'lightbox-dot' + (idx === lightboxCurrentIndex ? ' active' : '');
                    dot.setAttribute('aria-label', (idx + 1) + '번째 사진으로 이동');
                    dot.addEventListener('click', () => goToLightboxSlide(idx));
                    dotsEl.appendChild(dot);
                });
            }

            // 애니메이션 없이 클릭한 N번째 사진 위치로 즉시 이동 후 모달 표시
            trackEl.classList.add('no-transition');
            updateLightboxView();
            modal.classList.add('active');
            modal.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';

            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    trackEl.classList.remove('no-transition');
                });
            });
        }

        function updateLightboxView() {
            const total = lightboxImages.length;
            if (total === 0) return;

            const trackEl = document.getElementById('lightboxTrack');
            const counterEl = document.getElementById('lightboxCounter');
            const prevBtn = document.getElementById('lightboxPrevBtn');
            const nextBtn = document.getElementById('lightboxNextBtn');
            const dots = document.querySelectorAll('#lightboxDots .lightbox-dot');

            trackEl.style.transform = `translateX(-${lightboxCurrentIndex * 100}%)`;
            counterEl.textContent = `${lightboxCurrentIndex + 1} / ${total}`;

            if (total <= 1) {
                prevBtn.classList.add('hidden');
                nextBtn.classList.add('hidden');
            } else {
                prevBtn.classList.remove('hidden');
                nextBtn.classList.remove('hidden');
            }

            dots.forEach((dot, idx) => {
                dot.classList.toggle('active', idx === lightboxCurrentIndex);
            });
        }

        function goToLightboxSlide(index) {
            const total = lightboxImages.length;
            if (total <= 1) return;
            // 양끝에서 순환(Wrap-around) 지원하여 자연스럽게 모든 사진 탐색 가능
            lightboxCurrentIndex = (index + total) % total;
            updateLightboxView();
        }

        function stepLightboxSlide(delta) {
            goToLightboxSlide(lightboxCurrentIndex + delta);
        }

        function closeGoodsImageLightbox() {
            const modal = document.getElementById('goodsImageLightbox');
            if (!modal) return;
            modal.classList.remove('active');
            modal.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        }

        function handleLightboxBackdrop(event) {
            if (event.target.id === 'goodsImageLightbox') {
                closeGoodsImageLightbox();
            }
        }

        // PC/맥북 마우스 드래그 슬라이드 + 썸네일 클릭 시 라이트박스 오픈 + 라이트박스 내부 스와이프/키보드 제어
        document.addEventListener('DOMContentLoaded', () => {
            document.querySelectorAll('.slider-container').forEach(slider => {
                let isDown = false;
                let hasDragged = false;
                let startX;
                let scrollLeft;

                slider.addEventListener('mousedown', (e) => {
                    isDown = true;
                    hasDragged = false;
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
                    const x = e.pageX - slider.offsetLeft;
                    const diff = Math.abs(x - startX);
                    if (diff > 6) {
                        hasDragged = true;
                    }
                    e.preventDefault();
                    const walk = (x - startX) * 1.5;
                    slider.scrollLeft = scrollLeft - walk;
                });

                // 각 썸네일 클릭 시 (드래그가 아닌 순수 클릭일 때만) 클릭한 순번의 사진으로 라이트박스 오픈
                const thumbs = slider.querySelectorAll('.slide-thumb.clickable');
                const imageUrls = Array.from(thumbs)
                    .map(t => t.dataset.src)
                    .filter(Boolean);
                const goodsName = slider.dataset.goodsName || '굿즈 이미지';

                thumbs.forEach((thumb, idx) => {
                    thumb.addEventListener('click', (e) => {
                        if (hasDragged) {
                            hasDragged = false;
                            e.preventDefault();
                            return;
                        }
                        const targetIdx = thumb.dataset.index !== undefined ? parseInt(thumb.dataset.index, 10) : idx;
                        openGoodsImageLightbox(goodsName, imageUrls, targetIdx);
                    });
                });
            });

            // 라이트박스 내부 터치 스와이프 및 마우스 드래그 지원
            const stage = document.getElementById('lightboxStage');
            const track = document.getElementById('lightboxTrack');
            if (stage && track) {
                let pointerDown = false;
                let startX = 0;
                let currentDeltaX = 0;

                const onStart = (clientX) => {
                    if (lightboxImages.length <= 1) return;
                    pointerDown = true;
                    startX = clientX;
                    currentDeltaX = 0;
                    stage.classList.add('dragging');
                    track.classList.add('no-transition');
                };

                const onMove = (clientX) => {
                    if (!pointerDown) return;
                    currentDeltaX = clientX - startX;
                    const stageWidth = stage.clientWidth || 300;
                    const percentOffset = (currentDeltaX / stageWidth) * 100;
                    track.style.transform = `translateX(calc(-${lightboxCurrentIndex * 100}% + ${percentOffset}%))`;
                };

                const onEnd = () => {
                    if (!pointerDown) return;
                    pointerDown = false;
                    stage.classList.remove('dragging');
                    track.classList.remove('no-transition');

                    const threshold = 45;
                    if (currentDeltaX <= -threshold) {
                        stepLightboxSlide(1);
                    } else if (currentDeltaX >= threshold) {
                        stepLightboxSlide(-1);
                    } else {
                        updateLightboxView();
                    }
                    currentDeltaX = 0;
                };

                stage.addEventListener('touchstart', (e) => {
                    if (e.touches && e.touches.length === 1) {
                        onStart(e.touches[0].clientX);
                    }
                }, { passive: true });

                stage.addEventListener('touchmove', (e) => {
                    if (e.touches && e.touches.length === 1) {
                        onMove(e.touches[0].clientX);
                    }
                }, { passive: true });

                stage.addEventListener('touchend', onEnd);
                stage.addEventListener('touchcancel', onEnd);

                stage.addEventListener('mousedown', (e) => {
                    if (e.target.closest('.lightbox-nav-btn')) return;
                    onStart(e.clientX);
                });
                window.addEventListener('mousemove', (e) => {
                    if (!pointerDown) return;
                    onMove(e.clientX);
                });
                window.addEventListener('mouseup', () => {
                    if (!pointerDown) return;
                    onEnd();
                });
            }

            // 키보드 단축키 지원 (ESC 닫기, 좌/우 방향키로 사진 전환)
            document.addEventListener('keydown', (e) => {
                const modal = document.getElementById('goodsImageLightbox');
                if (!modal || !modal.classList.contains('active')) return;

                if (e.key === 'Escape') {
                    e.preventDefault();
                    closeGoodsImageLightbox();
                } else if (e.key === 'ArrowLeft') {
                    e.preventDefault();
                    stepLightboxSlide(-1);
                } else if (e.key === 'ArrowRight') {
                    e.preventDefault();
                    stepLightboxSlide(1);
                }
            });
        });

        // 굿즈 삭제 중앙 커스텀 모달 제어
        function openGoodsDeleteModal(goodsId, goodsName) {
            const form = document.getElementById('goodsDeleteForm');
            form.action = '/seller/goods/' + goodsId + '/delete';
            document.getElementById('goodsDeleteModalName').textContent = goodsName || '선택한 굿즈';
            document.getElementById('goodsDeleteModal').classList.add('active');
        }

        function closeGoodsDeleteModal() {
            document.getElementById('goodsDeleteModal').classList.remove('active');
        }

        function handleGoodsDeleteBackdrop(event) {
            if (event.target.id === 'goodsDeleteModal') {
                closeGoodsDeleteModal();
            }
        }

        // 굿즈 엑셀/ZIP 일괄 등록 모달 제어
        function openGoodsImportModal() {
            document.getElementById('goodsImportModal').classList.add('active');
        }

        function closeGoodsImportModal() {
            document.getElementById('goodsImportModal').classList.remove('active');
            resetGoodsImportForm();
        }

        function handleGoodsImportBackdrop(event) {
            if (event.target.id === 'goodsImportModal') {
                closeGoodsImportModal();
            }
        }

        function resetGoodsImportForm() {
            const input = document.getElementById('goodsImportFileInput');
            if (input) input.value = '';
            const dropText = document.getElementById('goodsImportDropText');
            if (dropText) dropText.textContent = '+ 클릭하거나 엑셀(.xlsx) 또는 ZIP(.zip) 파일을 여기로 드래그';
            const submitBtn = document.getElementById('btnGoodsImportSubmit');
            if (submitBtn) submitBtn.disabled = true;
        }

        document.addEventListener('DOMContentLoaded', () => {
            const goodsDropzone = document.getElementById('goodsImportDropzone');
            const goodsFileInput = document.getElementById('goodsImportFileInput');
            const goodsSubmitBtn = document.getElementById('btnGoodsImportSubmit');
            const goodsDropText = document.getElementById('goodsImportDropText');

            const MAX_IMPORT_SIZE = 70 * 1024 * 1024; // 70MB

            if (goodsDropzone && goodsFileInput) {
                goodsDropzone.addEventListener('click', () => goodsFileInput.click());

                goodsFileInput.addEventListener('change', (e) => {
                    if (e.target.files && e.target.files.length > 0) {
                        const file = e.target.files[0];
                        if (file.size > MAX_IMPORT_SIZE) {
                            alert(`선택한 파일('${file.name}')의 용량이 70MB를 초과하여 등록할 수 없습니다.\n(현재 용량: ${(file.size / (1024 * 1024)).toFixed(1)}MB / 최대 허용: 70MB)\n70MB 이하의 파일을 선택해주세요.`);
                            resetGoodsImportForm();
                            return;
                        }
                        goodsDropText.textContent = '📄 ' + file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
                        goodsSubmitBtn.disabled = false;
                    } else {
                        resetGoodsImportForm();
                    }
                });

                goodsDropzone.addEventListener('dragover', (e) => {
                    e.preventDefault();
                    goodsDropzone.classList.add('drag-active');
                });

                goodsDropzone.addEventListener('dragleave', () => {
                    goodsDropzone.classList.remove('drag-active');
                });

                goodsDropzone.addEventListener('drop', (e) => {
                    e.preventDefault();
                    goodsDropzone.classList.remove('drag-active');
                    if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                        const file = e.dataTransfer.files[0];
                        if (file.size > MAX_IMPORT_SIZE) {
                            alert(`선택한 파일('${file.name}')의 용량이 70MB를 초과하여 등록할 수 없습니다.\n(현재 용량: ${(file.size / (1024 * 1024)).toFixed(1)}MB / 최대 허용: 70MB)\n70MB 이하의 파일을 선택해주세요.`);
                            resetGoodsImportForm();
                            return;
                        }
                        goodsFileInput.files = e.dataTransfer.files;
                        goodsDropText.textContent = '📄 ' + file.name + ' (' + (file.size / 1024).toFixed(1) + ' KB)';
                        goodsSubmitBtn.disabled = false;
                    }
                });
            }
        });
