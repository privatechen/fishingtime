package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaobaoProductMapper {

    @Select("SELECT id FROM taobao_product WHERE item_id = #{itemId} LIMIT 1")
    Long findIdByItemId(@Param("itemId") String itemId);

    @Insert("INSERT IGNORE INTO taobao_product (item_id, product_url, status) VALUES (#{itemId}, #{productUrl}, 1)")
    int insertIgnore(@Param("itemId") String itemId, @Param("productUrl") String productUrl);
}
