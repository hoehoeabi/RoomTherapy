package com.roomtherapy.backend.domain.goods.dto;

import com.roomtherapy.backend.domain.goods.entity.Goods3dObject;

public record Goods3dObjectDto(
        Long id,
        String name,
        String description,
        String modelUrl,
        int displayOrder
) {
    public Goods3dObjectDto {
        name = name != null ? name.trim() : "";
        description = description != null ? description.trim() : "";
        modelUrl = modelUrl != null ? modelUrl.trim() : "";
        displayOrder = Math.max(0, displayOrder);
    }

    public static Goods3dObjectDto from(Goods3dObject entity) {
        return new Goods3dObjectDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getModelUrl(),
                entity.getDisplayOrder()
        );
    }

    public String modelFormat() {
        if (modelUrl == null || modelUrl.isBlank()) {
            return "3D";
        }
        String cleanUrl = modelUrl.split("\\?")[0];
        int dotIdx = cleanUrl.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < cleanUrl.length() - 1) {
            return cleanUrl.substring(dotIdx + 1).toUpperCase();
        }
        return "3D";
    }
}
