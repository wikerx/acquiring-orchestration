package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;

import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryCacheService
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 交易查询缓存服务，位于 service-payment 服务层，以数据库查询结果为事实源并提供可降级的订单级 Redis 读模型。
 * @status : create
 */
public interface TransactionQueryCacheService {

    /**
     * 优先读取订单级查询缓存，未命中时执行数据库加载并在 generation 未变化时回填。
     *
     * @param commandDTO 商户交易查询命令
     * @param databaseLoader 完整数据库查询加载器
     * @return 当前请求对应的完整交易查询结果
     */
    PaymentQueryResultDTO getOrLoad(PaymentCreateCommandDTO commandDTO,
                                    Supplier<PaymentQueryResultDTO> databaseLoader);

    /**
     * 推进商户订单缓存 generation，使此前全部查询变体立即不可读。
     *
     * @param merchantId 平台商户号
     * @param merchantOrderNo 商户订单号
     * @return 是否成功推进 generation 并刷新过期时间
     */
    boolean advanceGeneration(String merchantId, String merchantOrderNo);
}
