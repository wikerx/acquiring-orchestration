-- 审核稿。复用预警账号和凭据版本表，新增字段前需同步修改 Admin 的读写实现。
-- 执行时应先选中经确认的目标库 payment_acquiring，并使用 UTF-8 客户端连接。
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 5;

ALTER TABLE dispute_alert_account
    ADD COLUMN provider_settings_json JSON NULL
        COMMENT '平台专用的普通接入参数，包含接口版本、用途和交付地址等资料，空值表示尚未登记',
    ADD COLUMN provider_activation_validated TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '平台专用接入配置是否通过启用校验，零表示尚未校验',
    ALGORITHM=INSTANT;

-- 原有公私钥材料及指纹保留。采用其他认证方式的凭据版本允许三项字段为空。
ALTER TABLE dispute_alert_credential_version
    MODIFY COLUMN public_secret_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '公钥材料的不透明引用，采用其他凭据类型时为空',
    MODIFY COLUMN private_secret_ref CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '私钥材料的不透明引用，采用其他凭据类型时为空',
    MODIFY COLUMN public_key_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '公钥文本的核验指纹，采用其他凭据类型时为空',
    ADD COLUMN provider_credential_cipher_json JSON NULL
        COMMENT '按平台凭据字段编码保存的认证加密密文，空值表示此版本沿用公私钥存储方式',
    ALGORITHM=INPLACE,
    LOCK=NONE;
