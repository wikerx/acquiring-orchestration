package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨服务共享的汇率实际使用事实。
 *
 * <p>消息只描述已经被支付或结算计算采用的跨币种业务汇率，不表示汇率查询、候选报价或
 * 同币种恒等换算。生产者必须通过本地事务 Outbox 冻结该消息，消费者按 usageEventId
 * 使用数据库唯一键承担最终幂等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExchangeRateUsageMessage extends BaseMqMessage {

    /**
     * 序列化版本号，用于保持生产者、Outbox 重投和消费者之间的消息兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 汇率使用事件唯一号，必须与 messageId 一致并在全平台稳定不变。
     */
    private String usageEventId;

    /**
     * 产生实际使用事实的服务名，仅允许 service-payment 或 service-settlement。
     */
    private String producerService;

    /**
     * 业务汇率类型，仅允许 TRANSACTION_RATE 或 SETTLEMENT_RATE。
     */
    private String rateType;

    /**
     * 汇率使用场景，必须与 rateType 一一对应为 TRANSACTION 或 SETTLEMENT。
     */
    private String usageScene;

    /**
     * 使用汇率的真实业务动作，例如 PAYMENT、AUTHORIZATION、CAPTURE 或 SETTLEMENT。
     */
    private String businessType;

    /**
     * 稳定业务编号；支付使用交易号，结算使用结算批次号。
     */
    private String businessNo;

    /**
     * 一单位该 ISO 4217 源币种按 usedRate 兑换为目标币种。
     */
    private String baseCurrency;

    /**
     * 汇率计算结果对应的 ISO 4217 目标币种。
     */
    private String quoteCurrency;

    /**
     * 业务实际使用的直接汇率，数据库精度为 DECIMAL(24,12)。
     */
    private BigDecimal usedRate;

    /**
     * exchange_business_rate 主键，用于追溯实际采用的业务汇率版本。
     */
    private Long businessRateId;

    /**
     * exchange_raw_rate 主键；人工业务汇率允许为空。
     */
    private Long rawRateId;

    /**
     * exchange_rate_rule 主键；人工业务汇率允许为空。
     */
    private Long ruleId;

    /**
     * 汇率来源编码，例如 BOC、XE 或受控人工来源编码。
     */
    private String rateSource;

    /**
     * 原始报价方向，取值为 DIRECT 或 INVERSE。
     */
    private String sourceQuoteDirection;

    /**
     * 所采用报价或业务汇率的生效时间。
     */
    private LocalDateTime rateEffectiveTime;

    /**
     * 面向运营审计的受控计算说明，不得包含卡数据、密钥或原始报文。
     */
    private String calculationDescription;

    /**
     * 汇率真正参与业务计算并形成持久化结果的业务时间。
     */
    private LocalDateTime appliedTime;
}
