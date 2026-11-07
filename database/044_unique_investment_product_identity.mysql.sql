-- 本迁移兼容 MySQL 5.7 / 8.0+；在选择目标库后执行（先停止产品/持仓写入）。
-- market 的 NULL、空串、空格统一为空市场；代码/市场忽略大小写与首尾空格。
-- 不删除/合并历史产品。若存在重复，先输出全部重复 ID 并停止，供核对关联持仓。
-- 同一条 ALTER 安装生成列和唯一索引；可重复执行。MySQL 5.7 不提供 8.0 的崩溃原子 DDL 保证。

DROP PROCEDURE IF EXISTS migrate_044_product_identity;
DELIMITER $$
CREATE PROCEDURE migrate_044_product_identity()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'investment_products'
      AND index_name = 'uk_investment_products_identity'
  ) THEN
    IF EXISTS (
      SELECT 1 FROM investment_products
      GROUP BY product_type, UPPER(TRIM(symbol)), UPPER(TRIM(COALESCE(market, '')))
      HAVING COUNT(*) > 1
    ) THEN
      -- GROUP BY + JOIN 兼容 MySQL 5.7，仍逐行列出全部冲突 ID。
      SELECT p.product_type, duplicates.symbol_key, duplicates.market_key, p.id AS duplicate_product_id
      FROM investment_products p
      INNER JOIN (
        SELECT product_type, UPPER(TRIM(symbol)) AS symbol_key,
          UPPER(TRIM(COALESCE(market, ''))) AS market_key
        FROM investment_products
        GROUP BY product_type, UPPER(TRIM(symbol)), UPPER(TRIM(COALESCE(market, '')))
        HAVING COUNT(*) > 1
      ) duplicates
        ON p.product_type = duplicates.product_type
        AND UPPER(TRIM(p.symbol)) = duplicates.symbol_key
        AND UPPER(TRIM(COALESCE(p.market, ''))) = duplicates.market_key
      ORDER BY p.product_type, duplicates.symbol_key, duplicates.market_key, p.id;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate product identities: review listed IDs before migration 044';
    END IF;

    ALTER TABLE investment_products
      ADD COLUMN symbol_key VARCHAR(64) GENERATED ALWAYS AS (UPPER(TRIM(symbol))) STORED,
      ADD COLUMN market_key VARCHAR(32) GENERATED ALWAYS AS (UPPER(TRIM(COALESCE(market, '')))) STORED,
      DROP INDEX uk_investment_products_type_symbol_market,
      ADD UNIQUE KEY uk_investment_products_identity (product_type, symbol_key, market_key);
  END IF;
END$$
DELIMITER ;
CALL migrate_044_product_identity();
DROP PROCEDURE migrate_044_product_identity;
