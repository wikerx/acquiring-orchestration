package com.scott.payment.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.mapper.TransactionIdempotencyMapper;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
     * 构建支付创建幂等键。
     *
     * @param merchantId      商户号
     * @param merchantOrderNo 商户订单号
     * @param transactionType 交易类型
     * @return 幂等键
     */
    @Override
    public String buildPaymentCreateKey(String merchantId, String merchantOrderNo, String transactionType) {
        return String.join(":",
                normalize(merchantId),
                normalize(merchantOrderNo),
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
     * @param transactionOrderNo     交易生命周期主标识
     * @param transactionNo          交易动作单号
     * @param transactionStatus      交易状态
     * @param transactionAmountMinor 交易金额，最小币种单位
     * @param transactionCurrency    交易币种
     * @param resultSnapshot         返回结果 JSON 快照
     */
    @Override
    public void complete(String scope,
                         String key,
                         String transactionOrderNo,
                         String transactionNo,
                         String transactionStatus,
                         Long transactionAmountMinor,
                         String transactionCurrency,
                         String resultSnapshot) {
        TransactionIdempotencyDO update = new TransactionIdempotencyDO();
        update.setTransactionOrderNo(transactionOrderNo);
        update.setTransactionNo(transactionNo);
        update.setTransactionStatus(transactionStatus);
        update.setTransactionAmountMinor(transactionAmountMinor);
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
        record.setTransactionType(transactionType);
        record.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        record.setTransactionDateTime(transactionDateTime);
        record.setTimeZone(timeZone);
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
}
