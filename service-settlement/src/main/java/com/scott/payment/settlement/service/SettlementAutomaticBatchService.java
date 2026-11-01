package com.scott.payment.settlement.service;

import java.time.Instant;

/**
 * 自动日批用例边界，按档案时区和日切幂等建批后委托数据库批量认领，不处理汇率或余额。
 */
public interface SettlementAutomaticBatchService {

    /**
     * 处理一页已成熟的商户日批维度。
     *
     * @param now 当前绝对时间
     * @return 已创建或复用并进入认领流程的批次数
     */
    int createAndClaimMaturedBatches(Instant now);
}
