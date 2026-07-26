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
     * sys Job Executor Node Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysJobExecutorNodeMapper sysJobExecutorNodeMapper;
    /**
     * job Node Context 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    @Override
    /**
     * 完成 report Heartbeat 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     */
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

    @Override
    /**
     * 完成 list Nodes 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysJobExecutorNodeDO> listNodes() {
        return sysJobExecutorNodeMapper.selectList(new LambdaQueryWrapper<SysJobExecutorNodeDO>()
                .orderByDesc(SysJobExecutorNodeDO::getLastHeartbeatTime)
                .orderByAsc(SysJobExecutorNodeDO::getNodeId));
    }

    /**
     * 完成 sleep Before Retry 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param attempt attempt 输入值，含义由调用方法名称和所属业务对象限定
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
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
