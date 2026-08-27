package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.time.LocalDateTime;

/** 清分补偿 Job 参数；默认 DRY_RUN，显式指定 SHADOW_WRITE 才写影子恢复。 */
@Data
public class ClearingCompensationRequest {
    private String mode;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private Integer limit;
    private Integer maxPages;
}
