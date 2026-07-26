package com.scott.payment.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionIdempotencyService
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易幂等服务默认实现，位于 service-payment 服务实现层，依赖 transaction_idempotency 唯一键兜底重复请求。
 * @status : create
 */
@Service
public class DefaultTransactionIdempotencyService implements TransactionIdempotencyService {

    /**
     * 幂等未删除标识。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 初始乐观锁版本。
     */
    private static final int INITIAL_VERSION = 0;

    /**
     * 幂等记录默认保留天数。
     */
    private static final long DEFAULT_EXPIRE_DAYS = 30L;

    /**
     * 一步支付交易类型。
     */
    private static final String PAYMENT = "PAYMENT";

    /**
     * 授权交易类型。
     */
    private static final String AUTHORIZATION = "AUTHORIZATION";

    /**
     * 预授权交易类型。
     */
    private static final String PRE_AUTHORIZATION = "PRE_AUTHORIZATION";

    /**
     * 首次交易全局幂等分组。
     */
    private static final String INITIAL_FLOW_GROUP = "INITIAL";

    /**
     * 交易幂等 Mapper。
     */
    private final TransactionIdempotencyMapper idempotencyMapper;

    /**
     * 创建交易幂等服务默认实现。
     *
     * @param idempotencyMapper 交易幂等 Mapper
     */
    public DefaultTransactionIdempotencyService(TransactionIdempotencyMapper idempotencyMapper) {
        this.idempotencyMapper = idempotencyMapper;
    }

    /**
     * 构建交易动作幂等键。
     *
     * @param merchantId      商户号
     * @param merchantOrderId 商户本次 API 请求唯一标识
     * @param transactionType 交易类型
     * @return 幂等键
     */
    @Override
    public String buildTransactionOperationKey(String merchantId, String merchantOrderId, String transactionType) {
        return String.join(":",
                normalize(merchantId),
                normalize(merchantOrderId),
                normalize(transactionType));
    }

    /**
     * 构建首次交易全局幂等键。
     *
     * @param merchantId      商户号
     * @param merchantOrderNo 商户订单号
     * @return 首次交易幂等键
     */
    @Override
    public String buildInitialTransactionKey(String merchantId, String merchantOrderNo) {
        return String.join(":",
                normalize(merchantId),
                normalize(merchantOrderNo),
                INITIAL_FLOW_GROUP);
    }

    /**
     * 查询幂等记录。
     *
     * @param scope 幂等范围
     * @param key   幂等键
     * @return 幂等记录
     */
    @Override
    public Optional<TransactionIdempotencyDO> find(String scope, String key) {
        return Optional.ofNullable(idempotencyMapper.selectOne(Wrappers.<TransactionIdempotencyDO>lambdaQuery()
                .eq(TransactionIdempotencyDO::getIdempotencyScope, scope)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, key)
                .eq(TransactionIdempotencyDO::getDeleted, NOT_DELETED)
                .last("limit 1")));
    }

    /**
     * 按平台当前交易 ID 查询首次交易幂等记录。
     *
     * @param transactionId 平台当前交易唯一标识
     * @return 首次交易幂等记录
     */
    @Override
    public Optional<TransactionIdempotencyDO> findInitialTransaction(String transactionId) {
        return Optional.ofNullable(idempotencyMapper.selectOne(Wrappers.<TransactionIdempotencyDO>lambdaQuery()
                .eq(TransactionIdempotencyDO::getTransactionId, transactionId)
                .in(TransactionIdempotencyDO::getTransactionType, PAYMENT, AUTHORIZATION, PRE_AUTHORIZATION)
                .eq(TransactionIdempotencyDO::getDeleted, NOT_DELETED)
                .orderByAsc(TransactionIdempotencyDO::getCreateTime)
                .last("limit 1")));
    }

    /**
     * 占用幂等键。
     *
     * @param record 幂等记录
     * @return true 表示首次占用成功
     */
    @Override
    public boolean tryBegin(TransactionIdempotencyDO record) {
        try {
            idempotencyMapper.insert(record);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    /**
     * 保存首次处理结果快照。
     *
     * @param scope                  幂等范围
     * @param key                    幂等键
     * @param operationId            内部生命周期关联标识
     * @param transactionId          平台当前交易 ID
     * @param transactionStatus      交易状态
     * @param transactionAmount      交易金额，主币种单位
     * @param transactionCurrency    交易币种
     * @param resultSnapshot         返回结果 JSON 快照
     */
    @Override
    public void complete(String scope,
                         String key,
                         String operationId,
                         String transactionId,
                         String transactionStatus,
                         BigDecimal transactionAmount,
                         String transactionCurrency,
                         String resultSnapshot) {
        TransactionIdempotencyDO update = new TransactionIdempotencyDO();
        update.setOperationId(operationId);
        update.setTransactionId(transactionId);
        update.setTransactionStatus(transactionStatus);
        update.setTransactionAmount(transactionAmount);
        update.setTransactionCurrency(transactionCurrency);
        update.setResultSnapshot(resultSnapshot);
        update.setUpdateTime(LocalDateTime.now());
        idempotencyMapper.update(update, Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .eq(TransactionIdempotencyDO::getIdempotencyScope, scope)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, key)
                .eq(TransactionIdempotencyDO::getDeleted, NOT_DELETED));
    }

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
    @Override
    public TransactionIdempotencyDO newProcessingRecord(String scope,
                                                        String key,
                                                        String merchantId,
                                                        String merchantOrderNo,
                                                        String merchantOrderId,
                                                        String transactionType,
                                                        LocalDateTime transactionDateTime,
                                                        String timeZone,
                                                        String requestFingerprint,
                                                        LocalDateTime now) {
        TransactionIdempotencyDO record = new TransactionIdempotencyDO();
        record.setIdempotencyScope(scope);
        record.setIdempotencyKey(key);
        record.setMerchantId(merchantId);
        record.setMerchantOrderNo(merchantOrderNo);
        record.setMerchantOrderId(merchantOrderId);
        record.setTransactionType(transactionType);
        record.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        record.setTransactionDateTime(transactionDateTime);
        record.setTransactionTimeZone(timeZone);
        record.setTransactionUtcTime(toUtcTime(transactionDateTime, timeZone));
        record.setRequestFingerprint(requestFingerprint);
        record.setExpireTime(now.plusDays(DEFAULT_EXPIRE_DAYS));
        record.setVersion(INITIAL_VERSION);
        record.setDeleted(NOT_DELETED);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    /**
     * 标准化 normalize 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 转换生成 to Utc Time 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param timeZone 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 转换或构建后的目标对象
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? "Asia/Shanghai" : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
