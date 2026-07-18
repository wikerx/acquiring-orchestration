package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
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
     * 季度分表配置。
     */
    private final PaymentQuarterShardingProperties shardingProperties;

    /**
     * 季度解析器。
     */
    private final ShardingQuarterResolver quarterResolver;

    /**
     * 物理表名解析器。
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;

    /**
     * 创建交易本地消息服务默认实现。
     *
     * @param eventOutboxMapper  交易本地事件 Mapper
     * @param shardingProperties 季度分表配置
     * @param quarterResolver    季度解析器
     * @param tableNameResolver  物理表名解析器
     */
    public DefaultTransactionEventOutboxService(TransactionEventOutboxMapper eventOutboxMapper,
                                               PaymentQuarterShardingProperties shardingProperties,
                                               ShardingQuarterResolver quarterResolver,
                                               ShardingPhysicalTableNameResolver tableNameResolver) {
        this.eventOutboxMapper = eventOutboxMapper;
        this.shardingProperties = shardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
    }

    /**
     * 保存交易本地事件到交易业务时间所在的季度物理分表。
     *
     * @param eventDO 本地事件记录
     */
    @Override
    public void save(TransactionEventOutboxDO eventDO) {
        validateEvent(eventDO);
        eventOutboxMapper.insertPhysical(resolvePhysicalTable(eventDO.getTransactionDateTime()), eventDO);
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
        if (now == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "now is required");
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return eventOutboxMapper.selectDueForPublish(resolvePhysicalTable(eventTime), now, limit);
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
        return eventOutboxMapper.markSent(
                resolvePhysicalTable(eventDO.getTransactionDateTime()),
                eventDO.getId(),
                eventDO.getVersion(),
                actualSentTime) == 1;
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
        return eventOutboxMapper.markFailed(
                resolvePhysicalTable(eventDO.getTransactionDateTime()),
                eventDO.getId(),
                eventDO.getVersion(),
                nextRetryTime,
                safeFailReason,
                actualNow) == 1;
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

    private String resolvePhysicalTable(LocalDateTime transactionDateTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule();
        ShardingQuarter quarter = quarterResolver.fromDateTime(transactionDateTime);
        if (!quarterResolver.inRange(rule, quarter)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "transaction_date_time is outside sharding table range");
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

    private PaymentQuarterShardingProperties.TableRule resolveRule() {
        if (shardingProperties == null || shardingProperties.getTables() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding tables config is required");
        }
        PaymentQuarterShardingProperties.TableRule rule = shardingProperties.getTables().get(LOGICAL_TABLE);
        if (rule == null) {
            rule = shardingProperties.getTables().get("transaction-event-outbox");
        }
        if (rule == null) {
            rule = shardingProperties.getTables().values().stream()
                    .filter(item -> LOGICAL_TABLE.equals(item.getLogicalTable()))
                    .findFirst()
                    .orElse(null);
        }
        if (rule == null || Boolean.FALSE.equals(rule.getEnabled())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "transaction_event_outbox sharding rule is not enabled");
        }
        if (!StringUtils.hasText(rule.getLogicalTable())) {
            rule.setLogicalTable(LOGICAL_TABLE);
        }
        if (!StringUtils.hasText(rule.getTemplateTable())) {
            rule.setTemplateTable(LOGICAL_TABLE);
        }
        if (!StringUtils.hasText(rule.getShardingColumn())) {
            rule.setShardingColumn("transaction_date_time");
        }
        return rule;
    }
}
