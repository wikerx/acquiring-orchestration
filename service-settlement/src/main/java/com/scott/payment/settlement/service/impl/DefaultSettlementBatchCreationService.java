package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchDailySequenceDO;
import com.scott.payment.settlement.mapper.SettlementBatchDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.support.SettlementBatchNumberFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchCreationService
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 默认批次创建服务，串行锁定业务日序列并以 create_request_key 和数据库唯一键兜底幂等。
 * @status : create
 */
@Service
public class DefaultSettlementBatchCreationService implements SettlementBatchCreationService {

    /**
     * {@code MAX_DAILY_SEQUENCE}常量，统一 {@code DefaultSettlementBatchCreationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_DAILY_SEQUENCE = 99_999_999;

    private final SettlementBatchDailySequenceMapper sequenceMapper;
    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchNumberFormatter numberFormatter;
    private final Clock clock;

    /**
     * 创建批次服务。
     *
     * @param sequenceMapper 数据库日序列 Mapper
     * @param batchMapper 结算批次 Mapper
     * @param numberFormatter 批次号纯格式化器
     */
    @Autowired
    public DefaultSettlementBatchCreationService(SettlementBatchDailySequenceMapper sequenceMapper,
                                                  SettlementBatchMapper batchMapper,
                                                  SettlementBatchNumberFormatter numberFormatter) {
        this(sequenceMapper, batchMapper, numberFormatter, Clock.systemUTC());
    }

    DefaultSettlementBatchCreationService(SettlementBatchDailySequenceMapper sequenceMapper,
                                          SettlementBatchMapper batchMapper,
                                          SettlementBatchNumberFormatter numberFormatter,
                                          Clock clock) {
        this.sequenceMapper = sequenceMapper;
        this.batchMapper = batchMapper;
        this.numberFormatter = numberFormatter;
        this.clock = Objects.requireNonNull(clock, "settlement clock is required");
    }

    /**
     * 在 transaction 主库本地事务中幂等创建批次；重复请求校验全部不可变身份后返回原批次。
     *
     * @param command 冻结批次身份和候选窗口
     * @return 新建或复用批次
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementBatchCreateResult create(SettlementBatchCreateCommand command) {
        Objects.requireNonNull(command, "settlement batch command is required");
        sequenceMapper.insertIfAbsent(command.businessDate());
        SettlementBatchDailySequenceDO sequence = sequenceMapper.selectForUpdate(command.businessDate());
        if (sequence == null || sequence.getCurrentSequence() == null || sequence.getVersion() == null) {
            throw new IllegalStateException("settlement daily sequence could not be locked");
        }

        SettlementBatchDO existing = batchMapper.selectByCreateRequestKeyForUpdate(command.createRequestKey());
        if (existing != null) {
            verifyIdentity(existing, command);
            return result(existing, true);
        }

        int current = sequence.getCurrentSequence();
        if (current >= MAX_DAILY_SEQUENCE) {
            throw new IllegalStateException("settlement daily sequence is exhausted");
        }
        int next = current + 1;
        if (sequenceMapper.increment(command.businessDate(), current, sequence.getVersion()) != 1) {
            throw new IllegalStateException("settlement daily sequence CAS failed");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        SettlementBatchDO expected = batch(command, next, now);
        batchMapper.insertIdempotent(expected);
        SettlementBatchDO stored = batchMapper.selectByCreateRequestKeyForUpdate(command.createRequestKey());
        verifyIdentity(stored, command);
        boolean reused = !expected.getSettlementBatchNo().equals(stored.getSettlementBatchNo());
        return result(stored, reused);
    }

    /**
     * 构造尚未认领候选的结算批次初始快照，金额、候选数和重试数必须从零开始并保持 CREATED 状态。
     *
     * @param command 已校验的商户、档案、账户、目标币种和日切窗口
     * @param sequence 当日通过 CAS 占用的唯一序号
     * @param now 批次创建时间
     * @return 可按创建请求键幂等落库的批次记录
     */
    private SettlementBatchDO batch(SettlementBatchCreateCommand command, int sequence, LocalDateTime now) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo(numberFormatter.storageNumber(command.businessDate(), sequence));
        row.setCreateRequestKey(command.createRequestKey());
        row.setBusinessDate(command.businessDate());
        row.setBusinessTimeZone(command.businessTimeZone());
        row.setDailySequence(sequence);
        row.setMerchantId(command.merchantId());
        row.setSettlementProfileId(command.settlementProfileId());
        row.setSettlementAccountId(command.settlementAccountId());
        row.setTargetCurrency(command.targetCurrency());
        row.setTargetCurrencyExponent(command.targetCurrencyExponent());
        row.setBatchType(command.batchType().name());
        row.setOriginalBatchNo(command.originalBatchNo());
        row.setCreateMode("AUTO");
        row.setCutoffBeginTime(command.cutoffBeginTime());
        row.setCutoffEndTime(command.cutoffEndTime());
        row.setBatchStatus(SettlementBatchStatus.CREATED.name());
        row.setCandidateCount(0);
        row.setProjectableCandidateCount(0);
        row.setRetryCount(0);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private SettlementBatchCreateResult result(SettlementBatchDO row, boolean reused) {
        return new SettlementBatchCreateResult(
                row.getId(),
                row.getSettlementBatchNo(),
                numberFormatter.displayNumber(row.getBusinessDate(), row.getDailySequence()),
                reused);
    }

    /**
     * 校验命中创建幂等键的批次是否与本次不可变业务身份完全一致。
     * <p>
     * 幂等键只能复用原批次，不能被同一请求键替换商户、账户、币种、批次类型或切点；任一身份字段不同
     * 都表示调用方错误复用了幂等键，必须拒绝而不是返回已有批次。
     */
    private void verifyIdentity(SettlementBatchDO actual, SettlementBatchCreateCommand expected) {
        boolean matches = actual != null
                && Objects.equals(actual.getCreateRequestKey(), expected.createRequestKey())
                && Objects.equals(actual.getBusinessDate(), expected.businessDate())
                && Objects.equals(actual.getBusinessTimeZone(), expected.businessTimeZone())
                && Objects.equals(actual.getMerchantId(), expected.merchantId())
                && Objects.equals(actual.getSettlementProfileId(), expected.settlementProfileId())
                && Objects.equals(actual.getSettlementAccountId(), expected.settlementAccountId())
                && Objects.equals(actual.getTargetCurrency(), expected.targetCurrency())
                && Objects.equals(actual.getTargetCurrencyExponent(), expected.targetCurrencyExponent())
                && Objects.equals(actual.getBatchType(), expected.batchType().name())
                && Objects.equals(actual.getOriginalBatchNo(), expected.originalBatchNo())
                && Objects.equals(actual.getCutoffBeginTime(), expected.cutoffBeginTime())
                && Objects.equals(actual.getCutoffEndTime(), expected.cutoffEndTime());
        if (!matches) {
            throw new IllegalStateException("settlement create idempotency key contains mismatched immutable identity");
        }
    }
}
