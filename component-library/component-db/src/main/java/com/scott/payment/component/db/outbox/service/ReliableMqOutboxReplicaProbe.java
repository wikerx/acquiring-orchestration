package com.scott.payment.component.db.outbox.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.outbox.mapper.ReliableMqOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxReplicaProbe
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 可靠 MQ Outbox 从库可见性探针，用同事务事件确认业务修改已经完成主从复制
 * @status : create
 */
@Service
public class ReliableMqOutboxReplicaProbe {

    private final ReliableMqOutboxMapper outboxMapper;

    public ReliableMqOutboxReplicaProbe(ReliableMqOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 在独立 SLAVE 事务中检查事件是否可见。
     *
     * @param eventId 与业务数据同一主库事务写入的事件号
     * @return 从库已复制该事务时返回 {@code true}
     */
    @DS(DataSourceName.SLAVE)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isVisible(String eventId) {
        return outboxMapper.countByEventId(eventId) > 0;
    }
}
