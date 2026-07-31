package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.job.entity.SysJobExecutorNodeDO;
import com.scott.payment.job.mapper.SysJobExecutorNodeMapper;
import com.scott.payment.job.service.JobExecutorNodeService;
import com.scott.payment.job.support.JobNodeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
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
@Slf4j
@Service
public class JobExecutorNodeServiceImpl implements JobExecutorNodeService {

    /**
     * 离线节点单次最大更新行数，降低与心跳上报并发更新同表时的锁范围。
     */
    private static final int MARK_OFFLINE_BATCH_SIZE = 100;

    /**
     * 节点离线扫描遇到 MySQL 死锁时的最大重试次数。
     */
    private static final int MARK_OFFLINE_MAX_RETRY = 3;

    /**
     * 离线扫描死锁重试退避基准毫秒数。
     */
    private static final long DEADLOCK_RETRY_BACKOFF_MILLIS = 50L;

    /**
     * sys Job Executor Node Mapper 依赖，用于 Job Executor Node Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysJobExecutorNodeMapper sysJobExecutorNodeMapper;
    /**
     * job Node Context，用于保存 Job Executor Node Service Impl 中与 jobnodecontext 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
     * 上报当前执行节点的在线状态、并发容量和心跳时间。
     * <p>
     * Mapper 使用节点标识执行 upsert，使重启或重复心跳更新同一节点记录。
     * </p>
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
     * 标记超时在线节点为离线。
     *
     * <p>心跳上报和离线扫描会并发更新同一张节点表。这里采用小批量、固定排序、排除当前节点以及死锁重试，
     * 避免偶发 MySQL 死锁直接冒泡到调度线程。</p>
     */
    @Override
    public void markOfflineNodes() {
        LocalDateTime offlineBefore = LocalDateTime.now().minusSeconds(jobNodeContext.offlineSeconds());
        String currentNodeId = jobNodeContext.nodeId();
        for (int attempt = 1; attempt <= MARK_OFFLINE_MAX_RETRY; attempt++) {
            try {
                int affectedRows = sysJobExecutorNodeMapper.markOffline(
                        offlineBefore,
                        currentNodeId,
                        MARK_OFFLINE_BATCH_SIZE);
                if (affectedRows > 0) {
                    log.info("超时任务节点已标记离线，affectedRows：{}，offlineBefore：{}", affectedRows, offlineBefore);
                }
                return;
            } catch (PessimisticLockingFailureException exception) {
                if (attempt >= MARK_OFFLINE_MAX_RETRY) {
                    log.warn("超时任务节点离线扫描遇到锁冲突，已达到最大重试次数，offlineBefore：{}，原因：{}",
                            offlineBefore,
                            exception.getMessage());
                    return;
                }
                sleepBeforeRetry(attempt, exception);
            }
        }
    }

    /**
     * 查询全部执行节点，供管理端观察在线状态与最后心跳。
     *
     * @return 按最近心跳倒序、节点标识升序排列的节点列表
     */
    @Override
    public List<SysJobExecutorNodeDO> listNodes() {
        return sysJobExecutorNodeMapper.selectList(new LambdaQueryWrapper<SysJobExecutorNodeDO>()
                .orderByDesc(SysJobExecutorNodeDO::getLastHeartbeatTime)
                .orderByAsc(SysJobExecutorNodeDO::getNodeId));
    }

    /**
     * 对离线扫描的数据库锁冲突执行线性短退避。
     * <p>
     * 线程被中断时恢复中断标记，不吞掉调度器停止信号。
     * </p>
     *
     * @param attempt   当前重试序号，从 1 开始
     * @param exception 本次锁冲突异常
     */
    private void sleepBeforeRetry(int attempt, PessimisticLockingFailureException exception) {
        long backoffMillis = DEADLOCK_RETRY_BACKOFF_MILLIS * attempt;
        log.warn("超时任务节点离线扫描遇到锁冲突，准备重试，attempt：{}，backoffMillis：{}，原因：{}",
                attempt,
                backoffMillis,
                exception.getMessage());
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
