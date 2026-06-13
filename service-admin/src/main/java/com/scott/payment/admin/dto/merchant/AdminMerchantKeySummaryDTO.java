package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户密钥摘要响应。
 */
@Data
public class AdminMerchantKeySummaryDTO {

    private Long id;

    private String keyVersion;

    private String algorithm;

    private Integer keySize;

    private Long expiresSeconds;

    private Integer enabled;

    private String fingerprint;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private LocalDateTime gmtModified;
}
