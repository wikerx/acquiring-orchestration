package com.scott.payment.job.service;

import com.scott.payment.job.entity.SysJobExecutorNodeDO;

import java.util.List;

/**
 * 任务执行器节点服务接口
 */
public interface JobExecutorNodeService {

    /**
     * 上报当前节点心跳。
     */
    void reportHeartbeat();

    /**
     * 将超时节点标记为离线。
     */
    void markOfflineNodes();

    /**
     * 查询节点列表。
     *
     * @return 节点实体列表
     */
    List<SysJobExecutorNodeDO> listNodes();
}
