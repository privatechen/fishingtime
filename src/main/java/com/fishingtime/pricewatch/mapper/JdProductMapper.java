package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JdProductMapper {

    @Select("SELECT id FROM jd_product WHERE sku_id = #{skuId} LIMIT 1")
    Long findIdBySkuId(@Param("skuId") String skuId);

    @Insert("INSERT IGNORE INTO jd_product (sku_id, product_url, status) VALUES (#{skuId}, #{productUrl}, 1)")
    int insertIgnore(@Param("skuId") String skuId, @Param("productUrl") String productUrl);
}
