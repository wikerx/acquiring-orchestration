package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 清分补偿 Job 参数；默认 DRY_RUN，显式指定 SHADOW_WRITE 才写影子恢复。
 */
@Data
public class ClearingCompensationRequest {
    /**
     * 请求中的运行模式，用于限定本次操作的输入和校验范围。
     */
    private String mode;
    /**
     * 请求中的{@code beginTime}，用于限定本次操作的输入和校验范围。
     */
    private LocalDateTime beginTime;
    /**
     * 请求中的结束时间，用于限定本次操作的输入和校验范围。
     */
    private LocalDateTime endTime;

    private Integer limit;

    private Integer maxPages;
}
