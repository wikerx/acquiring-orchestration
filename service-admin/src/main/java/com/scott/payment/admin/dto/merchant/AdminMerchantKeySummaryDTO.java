package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台商户密钥摘要响应 DTO
 *
 * <p>用于商户列表或详情页展示当前生效密钥的轻量摘要，不包含完整敏感材料原文。</p>
 */
@Data
public class AdminMerchantKeySummaryDTO {

    /**
     * 密钥记录主键 ID。
     */
    private Long id;

    /**
     * 密钥版本号。
     */
    private String keyVersion;

    /**
     * 算法名称。
     */
    private String algorithm;

    /**
     * 密钥长度，单位 bit，可为空。
     */
    private Integer keySize;

    /**
     * 过期秒数，可为空。
     */
    private Long expiresSeconds;

    /**
     * 启用标记。
     */
    private Integer enabled;

    /**
     * 密钥指纹。
     */
    private String fingerprint;

    /**
     * 生效时间。
     */
    private LocalDateTime effectiveTime;

    /**
     * 失效时间，可为空。
     */
    private LocalDateTime expireTime;

    /**
     * 最近修改时间。
     */
    private LocalDateTime gmtModified;
}
