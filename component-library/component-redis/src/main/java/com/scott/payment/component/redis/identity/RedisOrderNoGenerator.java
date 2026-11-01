package com.scott.payment.component.redis.identity;

/**
 * Redis 分布式订单号生成服务
 */
public interface RedisOrderNoGenerator {

    /**
     * 生成分布式支付订单号。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @return 支付订单号
     */
    String nextOrderNo(String businessPrefix);
}
