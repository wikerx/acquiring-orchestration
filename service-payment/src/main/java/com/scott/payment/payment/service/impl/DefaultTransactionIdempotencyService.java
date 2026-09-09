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

    /** 首次交易请求幂等范围。 */
    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";

    /** 商户订单支付流守卫范围。 */
    private static final String MERCHANT_ORDER_FLOW_SCOPE = "MERCHANT_ORDER_FLOW";

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
     * 只允许 FAILED 流守卫按当前版本重新进入 PROCESSING，并清除上一笔交易的结果引用。
     */
    @Override
    public boolean tryRestartFailedFlow(TransactionIdempotencyDO existingRecord,
                                        TransactionIdempotencyDO replacement) {
        if (existingRecord == null
                || replacement == null
                || existingRecord.getId() == null
                || existingRecord.getVersion() == null) {
            return false;
        }
        int affectedRows = idempotencyMapper.update(null, Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .set(TransactionIdempotencyDO::getMerchantId, replacement.getMerchantId())
                .set(TransactionIdempotencyDO::getMerchantOrderNo, replacement.getMerchantOrderNo())
                .set(TransactionIdempotencyDO::getMerchantOrderId, replacement.getMerchantOrderId())
                .set(TransactionIdempotencyDO::getTransactionType, replacement.getTransactionType())
                .set(TransactionIdempotencyDO::getTransactionId, null)
                .set(TransactionIdempotencyDO::getOperationId, null)
                .set(TransactionIdempotencyDO::getTransactionStatus, PaymentTransactionStatusEnum.PROCESSING.getCode())
                .set(TransactionIdempotencyDO::getRequestAmount, null)
                .set(TransactionIdempotencyDO::getRequestCurrency, null)
                .set(TransactionIdempotencyDO::getTransactionAmount, null)
                .set(TransactionIdempotencyDO::getTransactionCurrency, null)
                .set(TransactionIdempotencyDO::getTransactionDateTime, replacement.getTransactionDateTime())
                .set(TransactionIdempotencyDO::getTransactionUtcTime, replacement.getTransactionUtcTime())
                .set(TransactionIdempotencyDO::getTransactionTimeZone, replacement.getTransactionTimeZone())
                .set(TransactionIdempotencyDO::getTransactionTimezoneOffset, replacement.getTransactionTimezoneOffset())
                .set(TransactionIdempotencyDO::getRequestFingerprint, replacement.getRequestFingerprint())
                .set(TransactionIdempotencyDO::getResultSnapshot, null)
                .set(TransactionIdempotencyDO::getExpireTime, replacement.getExpireTime())
                .set(TransactionIdempotencyDO::getUpdateTime, replacement.getUpdateTime())
                .setSql("version = version + 1")
                .eq(TransactionIdempotencyDO::getId, existingRecord.getId())
                .eq(TransactionIdempotencyDO::getIdempotencyScope, MERCHANT_ORDER_FLOW_SCOPE)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, existingRecord.getIdempotencyKey())
                .eq(TransactionIdempotencyDO::getTransactionStatus, PaymentTransactionStatusEnum.FAILED.getCode())
                .eq(TransactionIdempotencyDO::getVersion, existingRecord.getVersion())
                .eq(TransactionIdempotencyDO::getDeleted, NOT_DELETED));
        return affectedRows == 1;
    }

    /**
     * 渠道回调或主动查询只可把非终态首次交易幂等记录推进到终态，不能覆盖既有 SUCCESS/FAILED。
     */
    @Override
    public int synchronizeInitialTransactionStatus(String transactionId, String transactionStatus) {
        if (transactionId == null || transactionId.isBlank()
                || (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)
                && !PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus))) {
            return 0;
        }
        return idempotencyMapper.update(null, Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .set(TransactionIdempotencyDO::getTransactionStatus, transactionStatus)
                .set(TransactionIdempotencyDO::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(TransactionIdempotencyDO::getTransactionId, transactionId)
                .in(TransactionIdempotencyDO::getIdempotencyScope,
                        TRANSACTION_OPERATION_SCOPE, MERCHANT_ORDER_FLOW_SCOPE)
                .in(TransactionIdempotencyDO::getTransactionType, PAYMENT, AUTHORIZATION, PRE_AUTHORIZATION)
                .notIn(TransactionIdempotencyDO::getTransactionStatus,
                        PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentTransactionStatusEnum.FAILED.getCode())
                .eq(TransactionIdempotencyDO::getDeleted, NOT_DELETED));
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
        idempotencyMapper.update(null, Wrappers.<TransactionIdempotencyDO>lambdaUpdate()
                .set(TransactionIdempotencyDO::getOperationId, operationId)
                .set(TransactionIdempotencyDO::getTransactionId, transactionId)
                .set(TransactionIdempotencyDO::getTransactionStatus, transactionStatus)
                .set(TransactionIdempotencyDO::getTransactionAmount, transactionAmount)
                .set(TransactionIdempotencyDO::getTransactionCurrency, transactionCurrency)
                .set(TransactionIdempotencyDO::getResultSnapshot, resultSnapshot)
                .set(TransactionIdempotencyDO::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1")
                .eq(TransactionIdempotencyDO::getIdempotencyScope, scope)
                .eq(TransactionIdempotencyDO::getIdempotencyKey, key)
                .and(wrapper -> wrapper
                        .notIn(TransactionIdempotencyDO::getTransactionStatus,
                                PaymentTransactionStatusEnum.SUCCESS.getCode(), PaymentTransactionStatusEnum.FAILED.getCode())
                        .or()
                        .eq(TransactionIdempotencyDO::getTransactionStatus, transactionStatus))
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 按业务时区把交易时间换算为 UTC，供幂等记录跨时区定位。
     *
     * @param transactionDateTime 无时区交易业务时间
     * @param timeZone            IANA 时区；为空时使用 Asia/Shanghai
     * @return 同一时刻对应的 UTC 本地时间
     */
    private LocalDateTime toUtcTime(LocalDateTime transactionDateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? "Asia/Shanghai" : timeZone);
        return transactionDateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
