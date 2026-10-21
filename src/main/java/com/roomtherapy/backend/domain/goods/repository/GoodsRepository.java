package com.roomtherapy.backend.domain.goods.repository;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    List<Goods> findAllBySellerId(Long sellerId);

    List<Goods> findAllByStatus(GoodsStatus status);
}
