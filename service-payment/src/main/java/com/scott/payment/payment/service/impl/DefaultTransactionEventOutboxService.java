package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mapper.TransactionEventOutboxMapper;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
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
@DS(DataSourceName.TRANSACTION)
public class DefaultTransactionEventOutboxService implements TransactionEventOutboxService {

    /**
     * 交易本地事件 Mapper。
     */
    private final TransactionEventOutboxMapper eventOutboxMapper;

    /**
     * 创建交易本地消息服务默认实现。
     *
     * @param eventOutboxMapper 交易本地事件 Mapper
     */
    @Autowired
    public DefaultTransactionEventOutboxService(TransactionEventOutboxMapper eventOutboxMapper) {
        this.eventOutboxMapper = eventOutboxMapper;
    }

    /**
     * 通过交易逻辑表保存本地事件，由 ShardingSphere 按交易业务时间路由季度。
     *
     * @param eventDO 本地事件记录
     */
    @Override
    public void save(TransactionEventOutboxDO eventDO) {
        validateEvent(eventDO);
        requireSingleRow(eventOutboxMapper.insertLogical(eventDO));
    }

    /**
     * 查询指定交易时间所在季度中待投递的本地事件。
     *
     * @param eventTime 交易时间，用于 ShardingSphere 精确定位季度
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
        LocalDateTime beginTime = quarterBegin(eventTime);
        return eventOutboxMapper.selectDueForPublishLogical(
                beginTime, beginTime.plusMonths(3), now, limit);
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
        return eventOutboxMapper.markSentLogical(
                eventDO.getId(), eventDO.getTransactionDateTime(), eventDO.getVersion(), actualSentTime) == 1;
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
        return eventOutboxMapper.markFailedLogical(
                eventDO.getId(),
                eventDO.getTransactionDateTime(),
                eventDO.getVersion(),
                nextRetryTime,
                safeFailReason,
                actualNow) == 1;
    }

    /**
     * 恢复稳定执行事件，确保补偿仍使用原事件号和消息业务键。
     *
     * @param eventNo 稳定事件号
     * @param transactionDateTime 退款动作分片时间
     * @param eventType 退款执行事件类型
     * @param now 恢复时间
     * @return false 表示事件不存在；true 表示事件已可由 relay 投递或已处于待投递状态
     */
    @Override
    public boolean recoverForRedelivery(String eventNo,
                                        LocalDateTime transactionDateTime,
                                        String eventType,
                                        LocalDateTime now) {
        if (!StringUtils.hasText(eventNo)
                || transactionDateTime == null
                || !StringUtils.hasText(eventType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        TransactionEventOutboxDO existing = eventOutboxMapper.selectByEventNoLogical(eventNo, transactionDateTime);
        if (existing == null) {
            return false;
        }
        if ("INIT".equals(existing.getEventStatus())) {
            return true;
        }
        eventOutboxMapper.rearmForRedeliveryLogical(
                eventNo, transactionDateTime, eventType, now == null ? LocalDateTime.now() : now);
        return true;
    }

    /**
     * 校验新 Outbox 事件的业务身份、路由和分片时间。
     *
     * @param eventDO 待持久化事件
     * @throws ServiceException 必需字段缺失时抛出
     */
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

    /**
     * 校验更新 Outbox 状态所需的主键、版本号和分片时间。
     *
     * @param eventDO 已持久化事件
     * @throws ServiceException 无法安全定位或 CAS 更新记录时抛出
     */
    private void validatePersistedEvent(TransactionEventOutboxDO eventDO) {
        if (eventDO == null || eventDO.getId() == null || eventDO.getVersion() == null || eventDO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 计算事件所属季度的闭区间起点，供 due 查询同时携带分片谓词。
     *
     * @param transactionDateTime 事件分片时间
     * @return 所属季度首日零点
     */
    private LocalDateTime quarterBegin(LocalDateTime transactionDateTime) {
        int firstMonth = ((transactionDateTime.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(transactionDateTime.getYear(), firstMonth, 1, 0, 0);
    }

    /**
     * 强制 Outbox 插入只影响一行，避免事件丢失后主流程仍误判成功。
     *
     * @param affectedRows Mapper 返回的受影响行数
     */
    private void requireSingleRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "transaction outbox insert failed");
        }
    }

}
