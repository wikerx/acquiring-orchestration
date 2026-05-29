package com.scott.payment.component.db.sharding;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderShardingAlgorithm
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 支付订单分表算法预留类
 * @status : create
 */
public class PaymentOrderShardingAlgorithm {

    /**
     * 默认订单分表数量，当前按 16 张物理表做取模路由，后续可从 Nacos 或 ShardingSphere 配置读取。
     */
    private static final int DEFAULT_TABLE_COUNT = 16;

    public String tableName(String logicalTableName, String merchantId, String orderNo) {
        int hash = Math.abs((merchantId + orderNo).hashCode());
        return logicalTableName + "_" + hash % DEFAULT_TABLE_COUNT;
    }
}
