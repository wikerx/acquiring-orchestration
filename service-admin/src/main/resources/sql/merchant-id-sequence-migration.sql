-- 商户号六位年度流水迁移：格式为 yy + 四位年度流水，例如 260001。
-- 执行前提：base_merchant_info 已存在；脚本可重复执行，并会从历史六位纯数字商户号校准年度最大流水。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS base_merchant_id_sequence (
    business_year CHAR(2) NOT NULL COMMENT '商户号业务年份，格式yy',
    current_sequence SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前年份已分配最大流水，范围0至9999',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '序列CAS版本',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (business_year),
    CONSTRAINT chk_base_merchant_id_sequence CHECK (
        business_year REGEXP '^[0-9]{2}$'
        AND current_sequence BETWEEN 0 AND 9999
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商户号年度序列表';

INSERT INTO base_merchant_id_sequence
    (business_year, current_sequence, version, gmt_create, gmt_modified)
SELECT LEFT(merchant_id, 2),
       MAX(CAST(RIGHT(merchant_id, 4) AS UNSIGNED)),
       0,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3)
FROM base_merchant_info
WHERE merchant_id REGEXP '^[0-9]{6}$'
GROUP BY LEFT(merchant_id, 2)
ON DUPLICATE KEY UPDATE
    current_sequence = GREATEST(current_sequence, VALUES(current_sequence)),
    gmt_modified = CURRENT_TIMESTAMP(3);
