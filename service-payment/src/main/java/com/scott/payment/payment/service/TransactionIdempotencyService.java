package com.scott.payment.payment.service;

import com.scott.payment.payment.entity.TransactionIdempotencyDO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionIdempotencyService
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易幂等服务，位于 service-payment 服务层，负责用数据库唯一约束兜底资金类请求重复提交。
 * @status : create
 */
public interface TransactionIdempotencyService {

    /**
     * 构建交易动作幂等键。
     *
     * @param merchantId      商户号
     * @param merchantOrderId 商户本次 API 请求唯一标识
     * @param transactionType 交易类型
     * @return 幂等键
     */
    String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType);

    /**
     * 构建首次交易全局幂等键。
     * <p>
     * Payment/Auth/PreAuth 起点动作以 merchantId + merchantOrderNo + INITIAL 作为持久化幂等维度，
     * 不能依赖单次 API 请求号 merchantOrderId 或 Redis 锁作为最终兜底。
     *
     * @param merchantId       商户号
     * @param merchantOrderNo  商户订单号
     * @return 首次交易幂等键
     */
    default String buildInitialTransactionKey(String merchantId, String merchantOrderNo) {
        return String.join(":",
                normalizeKeyPart(merchantId),
                normalizeKeyPart(merchantOrderNo),
                "INITIAL");
    }

    /**
     * 执行 normalize Key Part 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 TransactionIdempotencyService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private static String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 查询幂等记录。
     *
     * @param scope 幂等范围
     * @param key   幂等键
     * @return 幂等记录
     */
    Optional<TransactionIdempotencyDO> find(String scope, String key);

    /**
     * 按平台当前交易 ID 查询首次交易幂等记录。
     *
     * @param transactionId 平台当前交易唯一标识
     * @return 首次交易幂等记录
     */
    Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId);

    /**
     * 占用幂等键。
     * <p>
     * 生产环境必须依赖数据库唯一索引保证同一范围、同一 key 只能成功插入一次。
     *
     * @param record 幂等记录
     * @return true 表示首次占用成功；false 表示已存在
     */
    boolean tryBegin(TransactionIdempotencyDO record);

    /**
     * 保存首次处理结果快照。
     *
     * @param scope                 幂等范围
     * @param key                   幂等键
     * @param operationId           内部生命周期关联标识
     * @param transactionId         平台当前交易 ID
     * @param transactionStatus     交易状态
     * @param transactionAmount     交易金额，主币种单位
     * @param transactionCurrency   交易币种
     * @param resultSnapshot        返回结果 JSON 快照
     */
    void complete(String scope,
                  String key,
                  String operationId,
                  String transactionId,
                  String transactionStatus,
                  BigDecimal transactionAmount,
                  String transactionCurrency,
                  String resultSnapshot);

    /**
     * 创建幂等占位记录。
     *
     * @param scope               幂等范围
     * @param key                 幂等键
     * @param merchantId          商户号
     * @param merchantOrderNo     商户订单号
     * @param merchantOrderId     商户本次 API 请求唯一标识
     * @param transactionType     交易类型
     * @param transactionDateTime 交易业务时间
     * @param timeZone            交易业务时区
     * @param requestFingerprint  请求体安全摘要
     * @param now                 当前系统时间
     * @return 幂等占位记录
     */
    TransactionIdempotencyDO newProcessingRecord(String scope,
                                                 String key,
                                                 String merchantId,
                                                 String merchantOrderNo,
                                                 String merchantOrderId,
                                                 String transactionType,
                                                 LocalDateTime transactionDateTime,
                                                 String timeZone,
                                                 String requestFingerprint,
                                                 LocalDateTime now);
}
