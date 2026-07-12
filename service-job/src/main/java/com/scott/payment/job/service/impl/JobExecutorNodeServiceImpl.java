package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.job.entity.SysJobExecutorNodeDO;
import com.scott.payment.job.mapper.SysJobExecutorNodeMapper;
import com.scott.payment.job.service.JobExecutorNodeService;
import com.scott.payment.job.support.JobNodeContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeServiceImpl
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Executor Node Service Impl，位于 service-job 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobExecutorNodeServiceImpl implements JobExecutorNodeService {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final SysJobExecutorNodeMapper sysJobExecutorNodeMapper;
    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final JobNodeContext jobNodeContext;

    /**
     * 创建执行节点领域服务。
     *
     * @param sysJobExecutorNodeMapper 节点 Mapper
     * @param jobNodeContext           当前节点上下文
     */
    public JobExecutorNodeServiceImpl(SysJobExecutorNodeMapper sysJobExecutorNodeMapper, JobNodeContext jobNodeContext) {
        this.sysJobExecutorNodeMapper = sysJobExecutorNodeMapper;
        this.jobNodeContext = jobNodeContext;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    @Override
    public void reportHeartbeat() {
        LocalDateTime now = LocalDateTime.now();
        SysJobExecutorNodeDO node = new SysJobExecutorNodeDO();
        node.setNodeId(jobNodeContext.nodeId());
        node.setAppName(jobNodeContext.appName());
        node.setHost(jobNodeContext.host());
        node.setPort(jobNodeContext.port());
        node.setInstanceId(jobNodeContext.instanceId());
        node.setStatus("ONLINE");
        node.setLastHeartbeatTime(now);
        node.setCurrentRunningCount(jobNodeContext.runningCount());
        node.setMaxConcurrentCount(jobNodeContext.maxConcurrentCount());
        node.setCreateTime(now);
        node.setUpdateTime(now);
        sysJobExecutorNodeMapper.upsertHeartbeat(node);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    @Override
    public void markOfflineNodes() {
        sysJobExecutorNodeMapper.markOffline(LocalDateTime.now().minusSeconds(jobNodeContext.offlineSeconds()));
    }

    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysJobExecutorNodeDO> listNodes() {
        return sysJobExecutorNodeMapper.selectList(new LambdaQueryWrapper<SysJobExecutorNodeDO>()
                .orderByDesc(SysJobExecutorNodeDO::getLastHeartbeatTime)
                .orderByAsc(SysJobExecutorNodeDO::getNodeId));
    }
}
