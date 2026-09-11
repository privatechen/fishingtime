package com.fishingtime.pricewatch.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaobaoProductMapper {

    @Select("SELECT id FROM taobao_product WHERE item_id = #{itemId} LIMIT 1")
    Long findIdByItemId(@Param("itemId") String itemId);

    @Select("SELECT id FROM taobao_product WHERE product_url = #{productUrl} LIMIT 1")
    Long findIdByProductUrl(@Param("productUrl") String productUrl);

    /*
     * item_id is unique and NOT NULL in the current table. Omitting it makes
     * MySQL use an empty value; after the first pending row every later
     * INSERT IGNORE is silently discarded. Use a deterministic placeholder
     * until PriceTool replaces it with the real Taobao item id.
     */
    @Insert("INSERT IGNORE INTO taobao_product (item_id, product_url, status) " +
            "VALUES (CONCAT('PENDING_', SUBSTRING(SHA2(#{productUrl}, 256), 1, 32)), #{productUrl}, 1)")
    int insertPending(@Param("productUrl") String productUrl);

    @Insert("INSERT IGNORE INTO taobao_product (item_id, product_url, status) VALUES (#{itemId}, #{productUrl}, 1)")
    int insertIgnore(@Param("itemId") String itemId, @Param("productUrl") String productUrl);
}
