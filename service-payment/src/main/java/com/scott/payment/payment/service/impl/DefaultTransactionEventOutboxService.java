package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mapper.TransactionEventOutboxMapper;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxService
 * @date : 2026-07-12 18:20
 * @email : scott_x@163.com
 * @description : 交易本地消息服务默认实现，位于 service-payment 服务实现层，只负责事务内落库，后续由 relay 投递 RocketMQ。
 * @status : create
 */
@Service
public class DefaultTransactionEventOutboxService implements TransactionEventOutboxService {

    /**
     * 本地消息逻辑表名。
     */
    private static final String LOGICAL_TABLE = "transaction_event_outbox";

    /**
     * 交易本地事件 Mapper。
     */
    private final TransactionEventOutboxMapper eventOutboxMapper;

    /**
     * 分表数据访问统一入口。
     */
    private final ShardingDataTemplate shardingDataTemplate;

    /**
     * 创建交易本地消息服务默认实现。
     *
     * @param eventOutboxMapper  交易本地事件 Mapper
     * @param shardingDataTemplate 分表数据访问统一入口
     */
    public DefaultTransactionEventOutboxService(TransactionEventOutboxMapper eventOutboxMapper,
                                               ShardingDataTemplate shardingDataTemplate) {
        this.eventOutboxMapper = eventOutboxMapper;
        this.shardingDataTemplate = shardingDataTemplate;
    }

    /**
     * 保存交易本地事件到交易业务时间所在的季度物理分表。
     *
     * @param eventDO 本地事件记录
     */
    @Override
    public void save(TransactionEventOutboxDO eventDO) {
        validateEvent(eventDO);
        shardingDataTemplate.insert(
                shardingContext(eventDO.getTransactionDateTime()),
                table -> eventOutboxMapper.insertPhysical(table, eventDO));
    }

    /**
     * 查询指定交易时间所在物理分表中待投递的本地事件。
     *
     * @param eventTime 交易时间，用于定位物理分表；保留参数名兼容现有调用方
     * @param now       当前时间
     * @param limit     最大返回条数
     * @return 待投递事件列表
     */
    @Override
    public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
        if (eventTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "eventTime is required");
        }
        if (now == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "now is required");
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return shardingDataTemplate.queryOne(
                shardingContext(eventTime),
                table -> eventOutboxMapper.selectDueForPublish(table, now, limit));
    }

    /**
     * 标记本地事件已投递。
     *
     * @param eventDO  待更新事件
     * @param sentTime 投递成功时间
     * @return true 表示更新成功
     */
    @Override
    public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
        validatePersistedEvent(eventDO);
        LocalDateTime actualSentTime = sentTime == null ? LocalDateTime.now() : sentTime;
        return shardingDataTemplate.update(
                shardingContext(eventDO.getTransactionDateTime()),
                table -> eventOutboxMapper.markSent(
                        table,
                        eventDO.getId(),
                        eventDO.getVersion(),
                        actualSentTime) == 1);
    }

    /**
     * 标记本地事件投递失败并安排下一次重试。
     *
     * @param eventDO       待更新事件
     * @param nextRetryTime 下次重试时间
     * @param failReason    失败原因摘要
     * @param now           当前时间
     * @return true 表示更新成功
     */
    @Override
    public boolean markFailed(TransactionEventOutboxDO eventDO,
                              LocalDateTime nextRetryTime,
                              String failReason,
                              LocalDateTime now) {
        validatePersistedEvent(eventDO);
        if (nextRetryTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "nextRetryTime is required");
        }
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        String safeFailReason = failReason;
        if (safeFailReason != null && safeFailReason.length() > 512) {
            safeFailReason = safeFailReason.substring(0, 512);
        }
        String failureReasonForUpdate = safeFailReason;
        return shardingDataTemplate.update(
                shardingContext(eventDO.getTransactionDateTime()),
                table -> eventOutboxMapper.markFailed(
                        table,
                        eventDO.getId(),
                        eventDO.getVersion(),
                        nextRetryTime,
                        failureReasonForUpdate,
                        actualNow) == 1);
    }

    private void validateEvent(TransactionEventOutboxDO eventDO) {
        if (eventDO == null
                || !StringUtils.hasText(eventDO.getEventNo())
                || !StringUtils.hasText(eventDO.getAggregateType())
                || !StringUtils.hasText(eventDO.getAggregateNo())
                || !StringUtils.hasText(eventDO.getEventType())
                || !StringUtils.hasText(eventDO.getEventStatus())
                || !StringUtils.hasText(eventDO.getTopic())
                || !StringUtils.hasText(eventDO.getMessageKey())
                || eventDO.getTransactionDateTime() == null
                || eventDO.getEventTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    private void validatePersistedEvent(TransactionEventOutboxDO eventDO) {
        if (eventDO == null || eventDO.getId() == null || eventDO.getVersion() == null || eventDO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 执行 sharding Context 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultTransactionEventOutboxService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionDateTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ShardingSingleTableContext shardingContext(LocalDateTime transactionDateTime) {
        return ShardingSingleTableContext.of(LOGICAL_TABLE, transactionDateTime, DataSourceName.MASTER);
    }
}
