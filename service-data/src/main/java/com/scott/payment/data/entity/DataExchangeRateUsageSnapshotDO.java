package com.scott.payment.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率实际使用快照持久化实体。
 *
 * <p>该表只保存上游已提交的跨币种使用事实，不参与支付或结算计算；usageEventId 的数据库
 * 唯一键承担 RocketMQ 重复投递的最终幂等。</p>
 */
@Data
@TableName("exchange_rate_usage_snapshot")
public class DataExchangeRateUsageSnapshotDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String usageEventId;

    private String producerService;

    private String rateType;

    private String usageScene;

    private String businessType;

    private String businessNo;

    private String baseCurrency;

    private String quoteCurrency;

    private BigDecimal usedRate;

    private Long businessRateId;

    private Long rawRateId;

    private Long ruleId;

    private String rateSource;

    private String sourceQuoteDirection;

    private LocalDateTime rateEffectiveTime;

    private String calculationDescription;

    private LocalDateTime appliedTime;

    private String createBy;

    private LocalDateTime createTime;

    private Long deleted;
}
