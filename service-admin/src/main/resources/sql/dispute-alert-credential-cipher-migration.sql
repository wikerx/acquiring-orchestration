-- 适用于已建立拒付预警一期表的数据库。向现有凭据版本表追加密文列。
SET NAMES utf8mb4;

ALTER TABLE dispute_alert_credential_version
    ADD COLUMN public_key_cipher MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '预警公钥的认证加密密文，空值表示此版本尚无本地加密材料',
    ADD COLUMN private_key_cipher MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '预警私钥的认证加密密文，空值表示此版本尚无本地加密材料',
    ALGORITHM=INSTANT;
