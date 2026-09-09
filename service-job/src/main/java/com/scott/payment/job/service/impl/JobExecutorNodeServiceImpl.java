package com.scott.payment.job.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
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

    private final SysJobExecutorNodeMapper sysJobExecutorNodeMapper;
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
     * <p>心跳上报和离线扫描会并发访问同一张节点表。离线扫描先以一致性读取得少量候选主键，再按主键固定顺序更新，
     * 避免状态心跳范围 UPDATE 与心跳 UPSERT 形成二级索引、主键的反向锁环。更新条件会再次校验心跳时间，
     * 已在查询后恢复心跳的节点不会被误标记为离线；短退避重试仅作为极端并发下的最后保护。</p>
     */
    @Override
    public void markOfflineNodes() {
        LocalDateTime offlineBefore = LocalDateTime.now().minusSeconds(jobNodeContext.offlineSeconds());
        String currentNodeId = jobNodeContext.nodeId();
        for (int attempt = 1; attempt <= MARK_OFFLINE_MAX_RETRY; attempt++) {
            try {
                List<Long> candidateNodeIds = sysJobExecutorNodeMapper.selectTimedOutNodeIds(
                        offlineBefore,
                        currentNodeId,
                        MARK_OFFLINE_BATCH_SIZE);
                if (candidateNodeIds == null || candidateNodeIds.isEmpty()) {
                    return;
                }
                int affectedRows = sysJobExecutorNodeMapper.markOfflineByIds(
                        candidateNodeIds,
                        offlineBefore,
                        currentNodeId);
                if (affectedRows > 0) {
                    log.info("event: JOB_EXECUTOR_NODE_OFFLINE_MARKED affectedRows: {} candidateCount: {} offlineBefore: {}",
                            affectedRows,
                            candidateNodeIds.size(),
                            offlineBefore);
                }
                return;
            } catch (PessimisticLockingFailureException exception) {
                if (attempt >= MARK_OFFLINE_MAX_RETRY) {
                    log.warn("event: JOB_EXECUTOR_NODE_OFFLINE_FAILED reason: lockConflict attempts: {} offlineBefore: {} exceptionType: {}",
                            attempt,
                            offlineBefore,
                            exception.getClass().getSimpleName());
                    return;
                }
                if (!sleepBeforeRetry(attempt, exception)) {
                    return;
                }
            }
        }
    }

    /**
     * 查询全部执行节点，供管理端观察在线状态与最后心跳。
     *
     * @return 按最近心跳倒序、节点标识升序排列的节点列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
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
     * @return 完成退避时返回 true；线程被中断时返回 false
     */
    private boolean sleepBeforeRetry(int attempt, PessimisticLockingFailureException exception) {
        long backoffMillis = DEADLOCK_RETRY_BACKOFF_MILLIS * attempt;
        log.info("event: JOB_EXECUTOR_NODE_OFFLINE_RETRY reason: lockConflict attempt: {} backoffMillis: {} exceptionType: {}",
                attempt,
                backoffMillis,
                exception.getClass().getSimpleName());
        try {
            Thread.sleep(backoffMillis);
            return true;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.info("event: JOB_EXECUTOR_NODE_OFFLINE_RETRY_ABORTED reason: threadInterrupted attempt: {}", attempt);
            return false;
        }
    }
}
