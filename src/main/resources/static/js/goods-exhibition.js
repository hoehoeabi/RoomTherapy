/**
 * ==============================================================================
 * RoomTherapy 3D Stage Object Exhibition Viewer (Three.js Engine)
 * ==============================================================================
 * [용도 및 쓰이는 곳]
 * - 3D 스테이지 오브제 전시관 화면: /goods/{id}/exhibition (templates/goods/exhibition.html)
 *
 * [핵심 기능 모듈]
 * 1. Three.js 멀티 포맷(.glb, .gltf, .fbx, .obj, .usdz) 비동기 로더 및 캐싱
 * 2. 외부 S3 URL CORS 우회를 위한 백엔드 스트리밍 엔드포인트 자동 변환
 * 3. OrbitControls 360도 인터랙티브 3D 회전 및 줌/팬 제어
 * 4. 이전/다음 오브제 슬라이드 탐색 및 키보드 단축키(ESC, 좌우 방향키)
 * 5. 모바일/PC 반응형 리사이즈 및 썸네일 자동 생성 엔진
 * ==============================================================================
 */

        import * as THREE from 'three';
        import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
        import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
        import { DRACOLoader } from 'three/addons/loaders/DRACOLoader.js';
        import { FBXLoader } from 'three/addons/loaders/FBXLoader.js';
        import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';
        import { USDZLoader } from 'three/addons/loaders/USDZLoader.js';

        const objects = Array.isArray(window.EXHIBITION_OBJECTS) ? window.EXHIBITION_OBJECTS : [];
        const goodsId = window.EXHIBITION_GOODS_ID;

        // 로더 인스턴스 구성
        const gltfLoader = new GLTFLoader();
        gltfLoader.setCrossOrigin('anonymous');
        const dracoLoader = new DRACOLoader();
        dracoLoader.setDecoderPath('https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/libs/draco/');
        gltfLoader.setDRACOLoader(dracoLoader);

        const fbxLoader = new FBXLoader();
        fbxLoader.setCrossOrigin('anonymous');
        const objLoader = new OBJLoader();
        objLoader.setCrossOrigin('anonymous');
        const usdzLoader = new USDZLoader();

        function getExtension(url) {
            if (!url) return '';
            const clean = url.split('?')[0].trim().toLowerCase();
            const idx = clean.lastIndexOf('.');
            return idx >= 0 ? clean.substring(idx) : '';
        }

        // 외부 S3 URL(Cross-Origin)인 경우 브라우저 CORS 차단을 우회하기 위해 백엔드 동일 출처(Same-Origin) 스트리밍 엔드포인트로 변환
        function resolveSameOriginModelUrl(item) {
            if (!item || !item.modelUrl) return '';
            const rawUrl = item.modelUrl.trim();
            const ext = getExtension(rawUrl) || '.glb';
            if ((rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) && item.id != null && goodsId) {
                return `/goods/${goodsId}/objects/${item.id}/model/object${ext}`;
            }
            return rawUrl;
        }

        // 인메모리 3D 모델 템플릿 캐시 및 진행 중인 다운로드 요청 통합(Deduplication)
        const modelTemplateCache = new Map(); // key -> THREE.Group (normalized template)
        const inflightLoads = new Map();      // key -> { promise: Promise<THREE.Group>, listeners: Set<Function> }

        function getModelCacheKey(item) {
            if (!item) return '';
            return item.id != null ? `id:${item.id}:${item.modelUrl || ''}` : `url:${item.modelUrl || ''}`;
        }

        function normalizeRaw3dRoot(rawRoot) {
            const pivot = new THREE.Group();
            rawRoot.traverse((child) => {
                if (child.isMesh) {
                    if (!child.material) {
                        child.material = new THREE.MeshStandardMaterial({
                            color: 0xd8dee9,
                            roughness: 0.45,
                            metalness: 0.1
                        });
                    }
                    child.castShadow = true;
                    child.receiveShadow = true;
                }
            });

            const box = new THREE.Box3().setFromObject(rawRoot);
            const size = new THREE.Vector3();
            const center = new THREE.Vector3();
            box.getSize(size);
            box.getCenter(center);

            const maxDim = Math.max(size.x, size.y, size.z);
            const targetSpan = 2.2;
            const scale = maxDim > 0.0001 ? (targetSpan / maxDim) : 1;

            rawRoot.scale.multiplyScalar(scale);

            const scaledBox = new THREE.Box3().setFromObject(rawRoot);
            const scaledCenter = new THREE.Vector3();
            scaledBox.getCenter(scaledCenter);
            rawRoot.position.sub(scaledCenter);

            pivot.add(rawRoot);
            return pivot;
        }

        function loadFromSingleUrl(targetUrl, ext, onProgress) {
            return new Promise((resolve, reject) => {
                const handleLoaded = (rawRoot) => {
                    try {
                        resolve(normalizeRaw3dRoot(rawRoot));
                    } catch (err) {
                        reject(err);
                    }
                };

                if (ext === '.glb' || ext === '.gltf') {
                    gltfLoader.load(targetUrl, (gltf) => handleLoaded(gltf.scene || gltf.scenes[0]), onProgress, reject);
                } else if (ext === '.fbx') {
                    fbxLoader.load(targetUrl, (group) => handleLoaded(group), onProgress, reject);
                } else if (ext === '.obj') {
                    objLoader.load(targetUrl, (group) => handleLoaded(group), onProgress, reject);
                } else if (ext === '.usdz') {
                    usdzLoader.load(targetUrl, (group) => handleLoaded(group), onProgress, reject);
                } else {
                    gltfLoader.load(targetUrl, (gltf) => handleLoaded(gltf.scene || gltf.scenes[0]), onProgress, reject);
                }
            });
        }

        // 캐시 및 중복 다운로드 방지(In-Flight Deduplication)가 적용된 3D 모델 로더
        async function loadNormalized3dModel(item, onProgressCallback) {
            const cacheKey = getModelCacheKey(item);
            if (modelTemplateCache.has(cacheKey)) {
                return modelTemplateCache.get(cacheKey).clone(true);
            }

            if (inflightLoads.has(cacheKey)) {
                const entry = inflightLoads.get(cacheKey);
                if (typeof onProgressCallback === 'function') {
                    entry.listeners.add(onProgressCallback);
                }
                const templatePivot = await entry.promise;
                return templatePivot.clone(true);
            }

            const listeners = new Set();
            if (typeof onProgressCallback === 'function') {
                listeners.add(onProgressCallback);
            }

            const notifyProgress = (xhrEvt) => {
                listeners.forEach((fn) => {
                    try {
                        fn(xhrEvt);
                    } catch (ignore) {}
                });
            };

            const primaryUrl = resolveSameOriginModelUrl(item);
            const fallbackUrl = item.modelUrl ? item.modelUrl.trim() : '';
            const ext = getExtension(fallbackUrl || primaryUrl);

            const loadPromise = (async () => {
                try {
                    const pivot = await loadFromSingleUrl(primaryUrl, ext, notifyProgress);
                    modelTemplateCache.set(cacheKey, pivot);
                    return pivot;
                } catch (primaryErr) {
                    if (fallbackUrl && fallbackUrl !== primaryUrl) {
                        const pivot = await loadFromSingleUrl(fallbackUrl, ext, notifyProgress);
                        modelTemplateCache.set(cacheKey, pivot);
                        return pivot;
                    }
                    throw primaryErr;
                } finally {
                    inflightLoads.delete(cacheKey);
                }
            })();

            inflightLoads.set(cacheKey, { promise: loadPromise, listeners });
            const resolvedTemplate = await loadPromise;
            return resolvedTemplate.clone(true);
        }

        // =========================================================
        // 1. 갤러리 액자 마우스 패럴랙스 틸트 및 3D 썸네일 자동 스냅샷 생성
        // =========================================================
        const wallGrid = document.getElementById('galleryWallGrid');
        const roomEl = document.getElementById('spatialRoom');
        if (wallGrid && roomEl) {
            roomEl.addEventListener('mousemove', (e) => {
                if (window.innerWidth <= 860) return;
                const rect = roomEl.getBoundingClientRect();
                const nx = ((e.clientX - rect.left) / rect.width - 0.5) * 2;
                const ny = ((e.clientY - rect.top) / rect.height - 0.5) * 2;
                const rotY = -8 + nx * 6;
                const rotX = 2 - ny * 4;
                wallGrid.style.transform = `rotateY(${rotY.toFixed(2)}deg) rotateX(${rotX.toFixed(2)}deg)`;
            });
        }

        // 썸네일 이미지 로드 실패 시 대체 플레이스홀더 처리
        function setupThumbnailFallbacks() {
            if (!objects || objects.length === 0) return;
            for (let i = 0; i < objects.length; i++) {
                const imgEl = document.getElementById(`frameThumbImg-${i}`);
                const phEl = document.getElementById(`frameThumbPlaceholder-${i}`);
                if (imgEl && phEl) {
                    imgEl.addEventListener('error', () => {
                        imgEl.style.display = 'none';
                        phEl.style.display = 'flex';
                    });
                }
            }
        }

        setupThumbnailFallbacks();

        // =========================================================
        // 2. 스페이셜 상세 3D 뷰어 (Three.js + OrbitControls)
        // =========================================================
        let currentIndex = 0;
        let renderer = null;
        let scene = null;
        let camera = null;
        let controls = null;
        let activeModel = null;
        let animFrameId = null;
        let loadSeq = 0;

        function ensureThreeViewerInitialized() {
            const container = document.getElementById('threeCanvasContainer');
            if (!container) return;
            if (renderer) {
                resizeThreeViewer();
                return;
            }

            scene = new THREE.Scene();

            const w = container.clientWidth || 640;
            const h = container.clientHeight || 480;
            camera = new THREE.PerspectiveCamera(40, w / h, 0.1, 100);
            camera.position.set(0, 1.0, 3.8);

            renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
            renderer.setSize(w, h);
            renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
            renderer.outputColorSpace = THREE.SRGBColorSpace;
            renderer.toneMapping = THREE.ACESFilmicToneMapping;
            renderer.toneMappingExposure = 1.15;

            container.innerHTML = '';
            container.appendChild(renderer.domElement);

            // 스튜디오 3점 조명
            const ambientLight = new THREE.AmbientLight(0xffffff, 1.25);
            const hemiLight = new THREE.HemisphereLight(0xf8fafc, 0x1e293b, 1.0);
            const keyLight = new THREE.DirectionalLight(0xffffff, 2.0);
            keyLight.position.set(4, 6, 5);
            const fillLight = new THREE.DirectionalLight(0x93c5fd, 0.85);
            fillLight.position.set(-4, 2, -3);
            scene.add(ambientLight, hemiLight, keyLight, fillLight);

            controls = new OrbitControls(camera, renderer.domElement);
            controls.enableDamping = true;
            controls.dampingFactor = 0.06;
            controls.enablePan = false;
            controls.minDistance = 1.2;
            controls.maxDistance = 9.0;
            controls.target.set(0, 0, 0);
            controls.update();

            const animate = () => {
                animFrameId = requestAnimationFrame(animate);
                if (controls) controls.update();
                if (renderer && scene && camera) {
                    renderer.render(scene, camera);
                }
            };
            animate();
        }

        function resizeThreeViewer() {
            const container = document.getElementById('threeCanvasContainer');
            if (!container || !renderer || !camera) return;
            const w = container.clientWidth || 640;
            const h = container.clientHeight || 480;
            camera.aspect = w / h;
            camera.updateProjectionMatrix();
            renderer.setSize(w, h);
        }

        window.addEventListener('resize', resizeThreeViewer);

        async function loadObjectIntoStage(index) {
            if (!objects || objects.length === 0) return;
            currentIndex = (index + objects.length) % objects.length;
            const item = objects[currentIndex];
            const mySeq = ++loadSeq;

            // 좌측 패널 텍스트 업데이트 (1. 오브제 이름 / 2. 오브제 설명)
            const orderEl = document.getElementById('detailObjOrder');
            const formatEl = document.getElementById('detailObjFormat');
            const nameEl = document.getElementById('detailObjName');
            const descEl = document.getElementById('detailObjDesc');
            const deleteForm = document.getElementById('singleObjectDeleteForm');
            const arLink = document.getElementById('arQuickLookLink');

            const numStr = String(currentIndex + 1).padStart(2, '0');
            const ext = getExtension(item.modelUrl);
            const fmt = ext ? ext.replace('.', '').toUpperCase() : '3D';

            if (orderEl) orderEl.textContent = `OBJECT ${numStr}`;
            if (formatEl) formatEl.textContent = fmt;
            if (nameEl) nameEl.textContent = item.name || `오브제 ${numStr}`;
            if (descEl) {
                descEl.textContent = (item.description && item.description.trim())
                    ? item.description
                    : '등록된 오브제 상세 설명이 없습니다.';
            }
            if (deleteForm && item.id != null) {
                deleteForm.action = `/seller/goods/${goodsId}/objects/${item.id}/delete`;
            }
            if (arLink) {
                if (ext === '.usdz' && item.modelUrl) {
                    arLink.href = resolveSameOriginModelUrl(item);
                    arLink.style.display = 'inline-flex';
                } else {
                    arLink.style.display = 'none';
                }
            }

            // 좌우 화살표 표시 제어
            const prevBtn = document.getElementById('btnPrevObj');
            const nextBtn = document.getElementById('btnNextObj');
            const showNav = objects.length > 1 ? 'flex' : 'none';
            if (prevBtn) prevBtn.style.display = showNav;
            if (nextBtn) nextBtn.style.display = showNav;

            ensureThreeViewerInitialized();

            // 기존 모델 제거
            if (activeModel && scene) {
                scene.remove(activeModel);
                activeModel = null;
            }

            const loadingEl = document.getElementById('modelLoadingState');
            const spinnerEl = document.getElementById('modelSpinner');
            const loadingTextEl = document.getElementById('modelLoadingText');
            const loadingSubEl = document.getElementById('modelLoadingSub');
            const progressTrackEl = document.getElementById('modelProgressTrack');
            const progressFillEl = document.getElementById('modelProgressFill');
            const retryBtnEl = document.getElementById('btnModelRetry');

            if (loadingEl) loadingEl.classList.remove('hidden');
            if (spinnerEl) spinnerEl.style.display = 'block';
            if (loadingTextEl) loadingTextEl.textContent = '3D 모델을 불러오는 중입니다...';
            if (loadingSubEl) loadingSubEl.textContent = '데이터 스트리밍 준비 중...';
            if (progressTrackEl) progressTrackEl.style.display = 'block';
            if (progressFillEl) progressFillEl.style.width = '0%';
            if (retryBtnEl) retryBtnEl.style.display = 'none';

            resetStageCamera();

            try {
                const loadedPivot = await loadNormalized3dModel(item, (evt) => {
                    if (mySeq !== loadSeq) return;
                    if (evt && evt.lengthComputable && evt.total > 0) {
                        const pct = Math.min(99, Math.round((evt.loaded / evt.total) * 100));
                        const loadedMb = (evt.loaded / (1024 * 1024)).toFixed(1);
                        const totalMb = (evt.total / (1024 * 1024)).toFixed(1);
                        if (loadingTextEl) loadingTextEl.textContent = `3D 모델 불러오는 중... ${pct}%`;
                        if (loadingSubEl) loadingSubEl.textContent = `${loadedMb} MB / ${totalMb} MB 수신 완료`;
                        if (progressFillEl) progressFillEl.style.width = `${pct}%`;
                    } else if (evt && evt.loaded > 0) {
                        const loadedMb = (evt.loaded / (1024 * 1024)).toFixed(1);
                        if (loadingTextEl) loadingTextEl.textContent = `3D 모델 불러오는 중...`;
                        if (loadingSubEl) loadingSubEl.textContent = `${loadedMb} MB 수신 중`;
                        if (progressFillEl) progressFillEl.style.width = '65%';
                    }
                });
                if (mySeq !== loadSeq) return;
                activeModel = loadedPivot;
                scene.add(activeModel);
                if (loadingEl) loadingEl.classList.add('hidden');
            } catch (err) {
                if (mySeq !== loadSeq) return;
                if (spinnerEl) spinnerEl.style.display = 'none';
                if (progressTrackEl) progressTrackEl.style.display = 'none';
                if (loadingTextEl) {
                    loadingTextEl.textContent = `⚠️ 3D 모델을 불러올 수 없습니다 (${fmt})`;
                }
                if (loadingSubEl) {
                    loadingSubEl.textContent = '네트워크 상태를 확인하거나 아래 버튼을 눌러 다시 시도해주세요.';
                }
                if (retryBtnEl) {
                    retryBtnEl.style.display = 'inline-block';
                }
            }
        }

        window.retryCurrentStageObject = function() {
            if (!objects || objects.length === 0) return;
            const item = objects[currentIndex];
            const cacheKey = getModelCacheKey(item);
            modelTemplateCache.delete(cacheKey);
            inflightLoads.delete(cacheKey);
            loadObjectIntoStage(currentIndex);
        };

        window.openSpatialViewer = function(index) {
            const overlay = document.getElementById('spatialDetailOverlay');
            if (!overlay) return;
            overlay.classList.add('active');
            overlay.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            setTimeout(() => {
                ensureThreeViewerInitialized();
                resizeThreeViewer();
                loadObjectIntoStage(index);
            }, 20);
        };

        window.closeSpatialViewer = function() {
            const overlay = document.getElementById('spatialDetailOverlay');
            if (!overlay) return;
            overlay.classList.remove('active');
            overlay.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        };

        window.handleOverlayBackdrop = function(event) {
            if (event.target && event.target.id === 'spatialDetailOverlay') {
                window.closeSpatialViewer();
            }
        };

        window.stepSpatialObject = function(delta) {
            if (!objects || objects.length <= 1) return;
            loadObjectIntoStage(currentIndex + delta);
        };

        window.zoomStageCamera = function(deltaScale) {
            if (!camera || !controls) return;
            const offset = new THREE.Vector3().subVectors(camera.position, controls.target);
            const nextLen = THREE.MathUtils.clamp(offset.length() * (1 + deltaScale), controls.minDistance, controls.maxDistance);
            offset.setLength(nextLen);
            camera.position.copy(controls.target).add(offset);
            controls.update();
        };

        window.resetStageCamera = function() {
            if (!camera || !controls) return;
            controls.target.set(0, 0, 0);
            camera.position.set(0, 0.9, 3.7);
            controls.update();
        };

        document.addEventListener('keydown', (e) => {
            const overlay = document.getElementById('spatialDetailOverlay');
            if (!overlay || !overlay.classList.contains('active')) return;
            if (e.key === 'Escape') {
                window.closeSpatialViewer();
            } else if (e.key === 'ArrowLeft') {
                window.stepSpatialObject(-1);
            } else if (e.key === 'ArrowRight') {
                window.stepSpatialObject(1);
            }
        });
