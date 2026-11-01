package com.scott.payment.data.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.data.entity.DataExchangeRateUsageSnapshotDO;
import com.scott.payment.data.mapper.DataExchangeRateUsageSnapshotMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 汇率使用事实事务落库服务。
 *
 * <p>数据库唯一键是最终幂等边界。唯一键冲突后必须回读并核对完整业务身份与汇率快照，
 * 只有完全一致才视为重复成功；同事件号对应不同事实时失败关闭。</p>
 */
@Service
public class ExchangeRateUsagePersistenceService {

    private static final long NOT_DELETED = 0L;

    private final DataExchangeRateUsageSnapshotMapper snapshotMapper;

    /**
     * 创建汇率使用事实事务落库服务。
     *
     * @param snapshotMapper 汇率使用快照数据访问入口
     */
    public ExchangeRateUsagePersistenceService(DataExchangeRateUsageSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * 将一条已经严格校验的消息写入主库。
     *
     * @param message 已通过消费者契约校验的汇率使用消息
     * @return true 表示首次插入，false 表示完全一致的重复消息
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public boolean persist(ExchangeRateUsageMessage message) {
        DataExchangeRateUsageSnapshotDO expected = toEntity(message);
        try {
            if (snapshotMapper.insert(expected) != 1) {
                throw new IllegalStateException("exchange rate usage snapshot insert affected unexpected rows");
            }
            return true;
        } catch (DuplicateKeyException exception) {
            DataExchangeRateUsageSnapshotDO existing = snapshotMapper.selectByUsageEventId(
                    expected.getUsageEventId());
            if (sameFact(existing, expected)) {
                return false;
            }
            throw new IllegalStateException(
                    "exchange rate usage event id conflicts with a different persisted fact", exception);
        }
    }

    /**
     * 查询最终幂等事实是否已经提交，供运维诊断或消费者辅助核对。
     *
     * @param usageEventId 全局稳定事件号
     * @return true 表示未删除快照已经存在
     */
    @DS(DataSourceName.MASTER)
    public boolean exists(String usageEventId) {
        return usageEventId != null
                && snapshotMapper.selectByUsageEventId(usageEventId.trim()) != null;
    }

    /**
     * 将公共消息契约转换为数据库快照，同时保留上游业务时间并单独记录消费落库时间。
     *
     * @param message 已校验消息
     * @return 待插入数据库实体
     */
    private DataExchangeRateUsageSnapshotDO toEntity(ExchangeRateUsageMessage message) {
        DataExchangeRateUsageSnapshotDO entity = new DataExchangeRateUsageSnapshotDO();
        entity.setUsageEventId(message.getUsageEventId());
        entity.setProducerService(message.getProducerService());
        entity.setRateType(message.getRateType());
        entity.setUsageScene(message.getUsageScene());
        entity.setBusinessType(message.getBusinessType());
        entity.setBusinessNo(message.getBusinessNo());
        entity.setBaseCurrency(message.getBaseCurrency());
        entity.setQuoteCurrency(message.getQuoteCurrency());
        entity.setUsedRate(message.getUsedRate());
        entity.setBusinessRateId(message.getBusinessRateId());
        entity.setRawRateId(message.getRawRateId());
        entity.setRuleId(message.getRuleId());
        entity.setRateSource(message.getRateSource());
        entity.setSourceQuoteDirection(message.getSourceQuoteDirection());
        entity.setRateEffectiveTime(toDatabaseTime(message.getRateEffectiveTime()));
        entity.setCalculationDescription(message.getCalculationDescription());
        entity.setAppliedTime(toDatabaseTime(message.getAppliedTime()));
        entity.setCreateBy(message.getProducerService());
        entity.setCreateTime(LocalDateTime.now());
        entity.setDeleted(NOT_DELETED);
        return entity;
    }

    /**
     * 比较同一 usageEventId 下所有不可变业务字段，避免其他唯一键冲突或事件号复用被误判为幂等。
     *
     * @param existing 已提交数据库事实
     * @param expected 当前消息期望事实
     * @return 所有业务字段一致时返回 true
     */
    private boolean sameFact(DataExchangeRateUsageSnapshotDO existing,
                             DataExchangeRateUsageSnapshotDO expected) {
        return existing != null
                && Objects.equals(existing.getUsageEventId(), expected.getUsageEventId())
                && Objects.equals(existing.getProducerService(), expected.getProducerService())
                && Objects.equals(existing.getRateType(), expected.getRateType())
                && Objects.equals(existing.getUsageScene(), expected.getUsageScene())
                && Objects.equals(existing.getBusinessType(), expected.getBusinessType())
                && Objects.equals(existing.getBusinessNo(), expected.getBusinessNo())
                && Objects.equals(existing.getBaseCurrency(), expected.getBaseCurrency())
                && Objects.equals(existing.getQuoteCurrency(), expected.getQuoteCurrency())
                && sameDecimal(existing.getUsedRate(), expected.getUsedRate())
                && Objects.equals(existing.getBusinessRateId(), expected.getBusinessRateId())
                && Objects.equals(existing.getRawRateId(), expected.getRawRateId())
                && Objects.equals(existing.getRuleId(), expected.getRuleId())
                && Objects.equals(existing.getRateSource(), expected.getRateSource())
                && Objects.equals(existing.getSourceQuoteDirection(), expected.getSourceQuoteDirection())
                && Objects.equals(existing.getRateEffectiveTime(), expected.getRateEffectiveTime())
                && Objects.equals(existing.getCalculationDescription(), expected.getCalculationDescription())
                && Objects.equals(existing.getAppliedTime(), expected.getAppliedTime());
    }

    /**
     * 按数值而非 BigDecimal scale 比较汇率，兼容数据库回读时补齐的固定小数位。
     *
     * @param left 数据库汇率
     * @param right 消息汇率
     * @return 数值相等时返回 true
     */
    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    /**
     * 将上游业务时间规范化为 MySQL DATETIME(3) 能无损保存的毫秒精度。
     *
     * <p>同一消息重投时数据库回读值只有毫秒；若保留消息纳秒直接比较，会把合法重复投递
     * 误判为事件号复用冲突。</p>
     *
     * @param value 上游业务时间
     * @return 截断到毫秒的业务时间
     */
    private LocalDateTime toDatabaseTime(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }
}
