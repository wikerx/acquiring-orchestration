package com.scott.payment.component.redis.identity;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisOrderNoGenerator
 * @date : 2026-05-31 20:49
 * @email : scott_x@163.com
 * @description : Redis 分布式订单号生成服务
 * @status : create
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
