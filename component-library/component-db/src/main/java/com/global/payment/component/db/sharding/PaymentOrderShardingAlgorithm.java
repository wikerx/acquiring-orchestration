package com.global.payment.component.db.sharding;

public class PaymentOrderShardingAlgorithm {

    private static final int DEFAULT_TABLE_COUNT = 16;

    public String tableName(String logicalTableName, String merchantId, String orderNo) {
        int hash = Math.abs((merchantId + orderNo).hashCode());
        return logicalTableName + "_" + hash % DEFAULT_TABLE_COUNT;
    }
}

