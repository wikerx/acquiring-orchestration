package com.scott.payment.admin.dto.monitor;

import lombok.Data;

/**
 * 分表 ID 规则响应模型。
 *
 * <p>用于向后台说明季度分表物理表的 AUTO_INCREMENT 前缀和单季度安全区间。</p>
 */
@Data
public class ShardingIdRuleResponse {

    private String mode;

    private String prefixFormat;

    private Integer sequenceWidth;

    private Long startSequence;

    private Long maxSequence;

    private String currentQuarter;

    private Long currentQuarterStartValue;

    private Long currentQuarterMaxValue;
}
