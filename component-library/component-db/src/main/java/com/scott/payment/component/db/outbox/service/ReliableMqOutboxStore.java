package com.scott.payment.component.db.outbox.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.mapper.ReliableMqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
@DS(DataSourceName.MASTER)
public class ReliableMqOutboxStore {

    /** Outbox Mapper。 */
    private final ReliableMqOutboxMapper mapper;

    /** 创建 Outbox 持久化边界。 */
    public ReliableMqOutboxStore(ReliableMqOutboxMapper mapper) {
        this.mapper = mapper;
    }

    /** 在调用方事务中写入消息意图。 */
    public int insert(ReliableMqOutboxDO event) {
        return mapper.insert(event);
    }

    /** 按事件号查询消息。 */
    public ReliableMqOutboxDO findByEventId(String eventId) {
        return mapper.selectByEventId(eventId);
    }

    /** 查询已到期消息。 */
    public List<ReliableMqOutboxDO> findDue(LocalDateTime now, int limit) {
        return mapper.selectDue(now, limit);
    }

    /** 在独立短事务中 CAS 抢占消息。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int claim(Long id, Integer version, LocalDateTime now) {
        return mapper.claim(id, version, now);
    }

    /** 在独立短事务中标记消息成功。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int markSent(Long id, Integer version, LocalDateTime now) {
        return mapper.markSent(id, version, now);
    }

    /** 在独立短事务中记录消息失败。 */
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int recoverStale(LocalDateTime staleBefore, LocalDateTime now) {
        return mapper.recoverStale(staleBefore, now);
    }
}
