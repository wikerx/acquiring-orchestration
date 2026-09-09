package com.scott.payment.component.db.outbox.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.mapper.ReliableMqOutboxMapper;
import com.scott.payment.component.db.outbox.model.ReliableMqOutboxMetricsSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxStore
 * @date : 2026-08-02 22:10
 * @email : scott_x@163.com
 * @description : 非交易可靠 MQ Outbox 主库持久化边界，抢占和状态推进使用独立短事务
 * @status : create
 */
@Service
public class ReliableMqOutboxStore {

    /** Outbox Mapper。 */
    private final ReliableMqOutboxMapper mapper;

    /** 创建 Outbox 持久化边界。 */
    public ReliableMqOutboxStore(ReliableMqOutboxMapper mapper) {
        this.mapper = mapper;
    }

    /** 在调用方事务中写入消息意图。 */
    @DS(DataSourceName.MASTER)
    public int insert(ReliableMqOutboxDO event) {
        return mapper.insert(event);
    }

    /** 按事件号查询消息。 */
    @DS(DataSourceName.MASTER)
    public ReliableMqOutboxDO findByEventId(String eventId) {
        return mapper.selectByEventId(eventId);
    }

    /** 查询已到期消息。 */
    @DS(DataSourceName.MASTER)
    public List<ReliableMqOutboxDO> findDue(LocalDateTime now, int limit) {
        return mapper.selectDue(now, limit);
    }

    /** 在独立短事务中 CAS 抢占消息。 */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int claim(Long id, Integer version, LocalDateTime now) {
        return mapper.claim(id, version, now);
    }

    /** 在独立短事务中标记消息成功。 */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int markSent(Long id, Integer version, LocalDateTime now) {
        return mapper.markSent(id, version, now);
    }

    /** 在独立短事务中记录消息失败。 */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int markFailed(Long id,
                          Integer version,
                          String targetStatus,
                          LocalDateTime nextRetryTime,
                          String failureReason,
                          LocalDateTime now) {
        return mapper.markFailed(id, version, targetStatus, nextRetryTime, failureReason, now);
    }

    /** 恢复超时占用，返回恢复数量。 */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int recoverStale(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.recoverStale(staleBefore, now);
    }

    /** 查询 pending、CLOSED 和最老积压时间的聚合快照。 */
    @DS(DataSourceName.MASTER)
    public ReliableMqOutboxMetricsSnapshot metricsSnapshot() {
        return mapper.selectMetricsSnapshot();
    }

    /**
     * 使用事件号和版本 CAS 人工恢复一条 CLOSED 消息。
     *
     * @param eventId 稳定事件号
     * @param expectedVersion 操作员读取时看到的版本号
     * @param recoveryReason 不含敏感信息的恢复原因
     * @param now 恢复时间
     * @return true 表示恢复成功；状态或版本已变化时返回 false
     */
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recoverClosed(String eventId,
                                 Integer expectedVersion,
                                 String recoveryReason,
                                 LocalDateTime now) {
        if (!StringUtils.hasText(eventId)
                || expectedVersion == null
                || expectedVersion < 0
                || !StringUtils.hasText(recoveryReason)) {
            throw new IllegalArgumentException("eventId, expectedVersion and recoveryReason are required");
        }
        String safeReason = recoveryReason.trim();
        if (safeReason.length() > 512) {
            safeReason = safeReason.substring(0, 512);
        }
        LocalDateTime recoveryTime = now == null ? LocalDateTime.now() : now;
        return mapper.recoverClosed(eventId, expectedVersion, safeReason, recoveryTime) == 1;
    }
}
