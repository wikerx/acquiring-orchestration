package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.data.entity.DataMerchantApiResponseAuditDO;
import com.scott.payment.data.mapper.DataMerchantApiResponseAuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 商户 OpenAPI 响应审计事务落库服务。
 *
 * <p>条件更新承担数据库最终幂等：首次消息补齐响应摘要，完全一致的重复消息正常确认，
 * 基础记录尚未到达时抛出异常触发 MQ 重试，不同响应事实复用同一身份时失败关闭。</p>
 */
@Service
public class MerchantApiResponseAuditPersistenceService {

    private static final int MAX_RESPONSE_PLAIN_LENGTH = 16_000;

    private final DataMerchantApiResponseAuditMapper auditMapper;

    /**
     * 创建商户响应审计事务落库服务。
     *
     * @param auditMapper 商户响应审计数据访问入口
     */
    public MerchantApiResponseAuditPersistenceService(DataMerchantApiResponseAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /**
     * 幂等写入一条已经通过消费边界校验的响应审计消息。
     *
     * @param message 已校验的商户响应审计消息
     * @return UPDATED 表示完成条件更新，DUPLICATE 表示数据库已有相同事实
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public PersistenceResult persist(MerchantApiResponseAuditMessage message) {
        DataMerchantApiResponseAuditDO expected = toExpectedFact(message);
        int updated = auditMapper.updateFirstResponseAudit(expected);
        if (updated == 1) {
            return PersistenceResult.UPDATED;
        }
        if (updated > 1) {
            throw new IllegalStateException("merchant response audit updated multiple interaction logs");
        }

        // 零行既可能是上游基础日志尚未提交，也可能是首次响应冲突，必须回查后再决定是否确认消息。
        List<DataMerchantApiResponseAuditDO> facts = auditMapper.selectResponseFacts(expected);
        if (facts == null || facts.isEmpty()) {
            throw new IllegalStateException("merchant response interaction log is not ready");
        }
        if (facts.size() != 1) {
            throw new IllegalStateException("merchant response audit identity is not unique");
        }
        if (sameFact(facts.get(0), expected)) {
            return PersistenceResult.DUPLICATE;
        }
        throw new IllegalStateException("merchant response audit conflicts with the first persisted response");
    }

    /**
     * 对异步边界传入的脱敏正文再次执行交易交互脱敏并收敛数据库字段长度。
     *
     * @param message 已校验消息
     * @return 可用于条件更新和事实回查的规范化投影
     */
    private DataMerchantApiResponseAuditDO toExpectedFact(MerchantApiResponseAuditMessage message) {
        DataMerchantApiResponseAuditDO expected = new DataMerchantApiResponseAuditDO();
        expected.setTransactionId(message.getTransactionId());
        expected.setTransactionDateTime(toDatabaseTime(message.getTransactionDateTime()));
        expected.setRequestId(trimToNull(message.getRequestId()));
        String maskedPlain = SensitiveDataMaskUtils.maskTransactionInteractionJsonSafely(
                message.getResponsePlainJsonMasked());
        expected.setResponsePlainJsonMasked(truncate(trimToNull(maskedPlain), MAX_RESPONSE_PLAIN_LENGTH));
        expected.setResponseCipherDigest(message.getResponseCipherDigest());
        expected.setResponseCipherMasked(trimToNull(message.getResponseCipherMasked()));
        expected.setResponseTime(toDatabaseTime(message.getResponseTime()));
        return expected;
    }

    /**
     * 比较同一响应身份下的不可变审计事实，阻止冲突消息覆盖首次响应。
     *
     * @param actual 数据库已提交事实
     * @param expected 当前消息期望事实
     * @return 摘要、掩码、响应时间和消息携带的脱敏正文一致时返回 true
     */
    private boolean sameFact(DataMerchantApiResponseAuditDO actual,
                             DataMerchantApiResponseAuditDO expected) {
        return actual != null
                && Objects.equals(actual.getTransactionId(), expected.getTransactionId())
                && Objects.equals(toDatabaseTime(actual.getTransactionDateTime()),
                expected.getTransactionDateTime())
                && Objects.equals(trimToNull(actual.getRequestId()), expected.getRequestId())
                && Objects.equals(actual.getResponseCipherDigest(), expected.getResponseCipherDigest())
                && Objects.equals(trimToNull(actual.getResponseCipherMasked()),
                expected.getResponseCipherMasked())
                && Objects.equals(toDatabaseTime(actual.getResponseTime()), expected.getResponseTime())
                && (expected.getResponsePlainJsonMasked() == null
                || Objects.equals(actual.getResponsePlainJsonMasked(),
                expected.getResponsePlainJsonMasked()));
    }

    /**
     * 将时间统一到交易表 DATETIME(3) 精度。
     *
     * @param value 原始时间
     * @return 毫秒精度时间，输入为空时返回空
     */
    private LocalDateTime toDatabaseTime(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 去除首尾空白并把空文本统一为空值。
     *
     * @param value 原始文本
     * @return 规范化文本或 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 将文本限制在数据库审计容量内，禁止依赖驱动静默截断。
     *
     * @param value 已脱敏文本
     * @param maxLength 最大字符数
     * @return 原文本或截断文本
     */
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 商户响应审计持久化结果。
     */
    public enum PersistenceResult {
        /**
         * 当前消息完成了一次条件更新。
         */
        UPDATED,

        /**
         * 数据库已经存在完全一致的响应事实。
         */
        DUPLICATE
    }
}
