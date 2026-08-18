package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementProperties
 * @date : 2026-08-06 00:00
 * @description : 退款管理运行配置；默认保留既有同步退款行为，审批能力只有显式启用并选择策略后才生效。
 * @status : create
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.refund.management")
public class RefundManagementProperties {

    /** 退款管理查询和来源字段能力开关。 */
    private boolean enabled;

    /** 人工审批执行开关，默认关闭。 */
    private boolean approvalEnabled;

    /** NONE、PARTIAL_ONLY 或 ALL；不提供跨币种金额阈值。 */
    private String approvalPolicy = "NONE";

    /** 审批任务有效分钟数。 */
    private long approvalExpireMinutes = 1440L;

    /** 退款审批执行 RocketMQ 消费者开关；生产发布需与审批能力一起评审启用。 */
    private boolean executionMqEnabled;

    /** 审批过期和已批准未执行恢复调度开关。 */
    private boolean recoveryEnabled;

    /** 单轮审批过期处理上限。 */
    private int expirationBatchSize = 100;

    /** 单轮已批准未执行恢复上限。 */
    private int executionRecoveryBatchSize = 100;

    /** 已批准退款进入恢复扫描前的最小静默秒数。 */
    private long executionRecoveryStaleSeconds = 300L;
}
