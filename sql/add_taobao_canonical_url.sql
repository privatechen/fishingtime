-- 保留 taobao_product.product_url 作为用户原始提交的淘宝/天猫短链。
-- 新增 canonical_url 专门保存短链解析后的稳定商品详情页，供 PriceTool 后续采价使用。
--
-- 执行一次即可。
ALTER TABLE taobao_product
    ADD COLUMN canonical_url VARCHAR(1024) NULL
        COMMENT '短链解析后的稳定淘宝/天猫商品详情页，PriceTool 后续采价使用'
        AFTER product_url,
    ADD KEY idx_taobao_canonical_url (canonical_url(191));
