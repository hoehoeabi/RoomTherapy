package com.roomtherapy.backend.domain.goods.repository;

import com.roomtherapy.backend.domain.goods.entity.Goods3dObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Goods3dObjectRepository extends JpaRepository<Goods3dObject, Long> {

    List<Goods3dObject> findByGoodsIdOrderByDisplayOrderAscIdAsc(Long goodsId);

    void deleteByGoodsId(Long goodsId);
}
