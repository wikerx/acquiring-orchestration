package com.scott.payment.component.db.sharding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingGovernanceProperties
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 交易季度物理表治理配置，独立于业务逻辑路由规则，只供 Job/Admin 治理链路使用。
 * @status : create
 */
@Component
@ConfigurationProperties(prefix = "transaction-sharding.governance")
public class TransactionShardingGovernanceProperties extends PaymentQuarterShardingProperties {

    /** 在规则结束前多少个季度发出续期告警。 */
    private int expiryWarningQuarters = 4;

    /**
     * 返回规则到期前的预警窗口。
     *
     * @return 预警季度数
     */
    public int getExpiryWarningQuarters() {
        return expiryWarningQuarters;
    }

    /**
     * 设置规则到期前的预警窗口；实际治理任务会把非正数收敛为一个季度。
     *
     * @param expiryWarningQuarters 预警季度数
     */
    public void setExpiryWarningQuarters(int expiryWarningQuarters) {
        this.expiryWarningQuarters = expiryWarningQuarters;
    }
}
