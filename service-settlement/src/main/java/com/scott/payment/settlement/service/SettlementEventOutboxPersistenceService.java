package com.scott.payment.settlement.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.mapper.SettlementEventOutboxMapper;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementEventOutboxPersistenceService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 定义结算事件 Outbox 的短事务状态边界；认领、成功和失败分别提交，MQ 网络发送期间不持有数据库锁。
 * @status : create
 */
@Service
public class SettlementEventOutboxPersistenceService {

    /**
     * {@code MAX_RETRY_BACKOFF_MINUTES}常量，统一 {@code SettlementEventOutboxPersistenceService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_RETRY_BACKOFF_MINUTES = 30;

    private final SettlementEventOutboxMapper outboxMapper;
    private final SettlementWorkerIdentity workerIdentity;

    public SettlementEventOutboxPersistenceService(SettlementEventOutboxMapper outboxMapper,
                                                    SettlementWorkerIdentity workerIdentity) {
        this.outboxMapper = outboxMapper;
        this.workerIdentity = workerIdentity;
    }

    /**
     * 使用行锁和版本 CAS 领取两分钟发送租约，网络发送阶段不持有数据库事务。
     *
     * @param now 本轮认领时间
     * @return 已领取事件；没有到期事件时为空
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public Optional<SettlementEventOutboxDO> claimNext(LocalDateTime now) {
        SettlementEventOutboxDO row = outboxMapper.selectNextDueForUpdate(now);
        if (row == null) {
            return Optional.empty();
        }
        String owner = workerIdentity.value();
        if (row.getVersion() == null || outboxMapper.claim(row.getEventNo(), owner,
                now.plusMinutes(2), row.getVersion(), now) != 1) {
            throw new IllegalStateException("settlement event outbox claim CAS failed");
        }
        row.setProcessingOwner(owner);
        row.setProcessingDeadline(now.plusMinutes(2));
        row.setEventStatus("PROCESSING");
        row.setVersion(row.getVersion() + 1);
        return Optional.of(row);
    }

    /**
     * MQ 成功后提交 SENT；CAS 失败时保留至少一次重发语义。
     *
     * @param row 已领取且携带所有者和认领后版本的 Outbox
     * @param now Broker 确认成功后的状态提交时间
     * @return 当前租约和版本成功转为 SENT 时返回 true
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markSent(SettlementEventOutboxDO row, LocalDateTime now) {
        return outboxMapper.markSent(row.getEventNo(), row.getProcessingOwner(),
                row.getVersion(), now) == 1;
    }

    /**
     * MQ 失败后释放租约并按 1、2、4 分钟指数退避，最多等待 30 分钟。
     *
     * @param row 已领取且携带所有者、版本和当前重试次数的 Outbox
     * @param failureCode 不含异常正文的稳定失败分类
     * @param now 本次失败状态提交时间
     * @return 当前租约和版本成功写入退避状态时返回 true
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean markFailed(SettlementEventOutboxDO row, String failureCode, LocalDateTime now) {
        int retryCount = row.getRetryCount() == null ? 0 : Math.max(0, row.getRetryCount());
        long delayMinutes = Math.min(1L << Math.min(retryCount, 5), MAX_RETRY_BACKOFF_MINUTES);
        return outboxMapper.markFailed(row.getEventNo(), row.getProcessingOwner(), row.getVersion(),
                failureCode, now.plusMinutes(delayMinutes), now) == 1;
    }
}
