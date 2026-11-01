package com.scott.payment.admin.dto.monitor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 分表ID规则响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 */
@Data
public class ShardingIdRuleResponse {

    private String mode;

    private String prefixFormat;

    private Integer sequenceWidth;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long startSequence;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long maxSequence;

    private String currentQuarter;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentQuarterStartValue;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentQuarterMaxValue;
}
