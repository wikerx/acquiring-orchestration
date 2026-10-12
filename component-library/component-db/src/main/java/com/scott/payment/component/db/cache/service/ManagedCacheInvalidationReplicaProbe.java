package com.scott.payment.component.db.cache.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.cache.mapper.ManagedCacheInvalidationOutboxMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ManagedCacheInvalidationReplicaProbe
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 永久缓存失效事件的从库可见性探针，在独立只读事务中确认业务提交已复制后再允许删除缓存
 * @status : create
 */
@Service
public class ManagedCacheInvalidationReplicaProbe {

    /** 缓存失效 Outbox 数据访问组件。 */
    private final ManagedCacheInvalidationOutboxMapper outboxMapper;

    /**
     * 创建从库可见性探针。
     *
     * @param outboxMapper 缓存失效 Outbox Mapper
     */
    public ManagedCacheInvalidationReplicaProbe(ManagedCacheInvalidationOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 判断失效事件是否已经复制到从库。
     *
     * <p>事件与业务修改在同一主库事务提交。只有从库能够读取该事件时，才说明同一事务中的
     * 业务数据也已经越过复制边界，此时删除永久缓存才不会从从库重建旧值。</p>
     *
     * @param eventId 失效事件唯一标识
     * @return 从库已经可见时返回 {@code true}
     */
    @DS(DataSourceName.SLAVE)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean isVisible(String eventId) {
        return outboxMapper.countByEventId(eventId) > 0;
    }
}
