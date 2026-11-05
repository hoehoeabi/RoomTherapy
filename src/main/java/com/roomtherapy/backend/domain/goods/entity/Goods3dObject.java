package com.roomtherapy.backend.domain.goods.entity;

import com.roomtherapy.backend.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 스테이지(굿즈)별 전시관에 노출되는 3D 오브제 엔티티
 * 1. 오브제 이름 (name)
 * 2. 오브제 설명 (description)
 * 3. 오브제 3D 모델 URL (modelUrl - .glb, .gltf, .fbx, .obj, .usdz)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "goods_3d_objects")
public class Goods3dObject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id", nullable = false)
    private Goods goods;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 1000)
    private String modelUrl;

    @Column(nullable = false)
    private int displayOrder;

    @Builder
    public Goods3dObject(Goods goods, String name, String description, String modelUrl, int displayOrder) {
        this.goods = goods;
        this.name = name != null ? name.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.modelUrl = modelUrl != null ? modelUrl.trim() : "";
        this.displayOrder = Math.max(0, displayOrder);
    }

    public void update(String name, String description, String modelUrl, int displayOrder) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        this.description = description != null ? description.trim() : "";
        if (modelUrl != null && !modelUrl.isBlank()) {
            this.modelUrl = modelUrl.trim();
        }
        this.displayOrder = Math.max(0, displayOrder);
    }
}
