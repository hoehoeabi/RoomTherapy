package com.roomtherapy.backend.domain.goods.repository;

import com.roomtherapy.backend.domain.goods.entity.GoodsAuthCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GoodsAuthCodeRepository extends JpaRepository<GoodsAuthCode, Long> {

    @Query("SELECT COUNT(gac) > 0 FROM GoodsAuthCode gac " +
           "WHERE gac.goods.id = :goodsId AND LOWER(gac.code) = LOWER(:code)")
    boolean existsByGoodsIdAndCodeIgnoreCase(@Param("goodsId") Long goodsId, @Param("code") String code);

    List<GoodsAuthCode> findByGoodsId(Long goodsId);

    long countByGoodsId(Long goodsId);

    void deleteByGoodsId(Long goodsId);
}
