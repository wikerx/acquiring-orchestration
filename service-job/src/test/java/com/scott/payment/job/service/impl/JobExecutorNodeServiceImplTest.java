package com.scott.payment.job.service.impl;

import com.scott.payment.job.mapper.SysJobExecutorNodeMapper;
import com.scott.payment.job.support.JobNodeContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeServiceImplTest
 * @date : 2026-07-12 19:20
 * @email : scott_x@163.com
 * @description : 任务执行节点服务测试，验证离线扫描按候选主键更新、空候选短路及锁冲突重试边界。
 * @status : create
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class JobExecutorNodeServiceImplTest {

    /**
     * 节点 Mapper，用于验证离线扫描更新条件和死锁重试。
     */
    @Mock
    private SysJobExecutorNodeMapper nodeMapper;

    /**
     * 当前任务节点上下文。
     */
    @Mock
    private JobNodeContext jobNodeContext;

    /**
     * 被测节点服务。
     */
    private JobExecutorNodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobExecutorNodeServiceImpl(nodeMapper, jobNodeContext);
    }

    /**
     * 验证候选节点更新遇到锁冲突后会重新查询候选并重试，避免一次并发冲突中断调度线程。
     */
    @Test
    void shouldRetryWhenMarkOfflineNodesMeetsDeadlock() {
        log.info("测试离线扫描锁冲突重试：模拟首次主键更新死锁，预期第二次更新成功");
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.selectTimedOutNodeIds(
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"),
                eq(100))).thenReturn(List.of(11L));
        when(nodeMapper.markOfflineByIds(
                eq(List.of(11L)),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007")))
                .thenThrow(new PessimisticLockingFailureException("deadlock"))
                .thenReturn(2);

        service.markOfflineNodes();

        ArgumentCaptor<LocalDateTime> offlineBeforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(nodeMapper, times(2)).markOfflineByIds(
                eq(List.of(11L)),
                offlineBeforeCaptor.capture(),
                eq("service-job@127.0.0.1:8007"));
        assertThat(offlineBeforeCaptor.getValue()).isBefore(LocalDateTime.now());
        log.info("离线扫描锁冲突重试验证完成：主键更新调用次数=2");
    }

    /**
     * 验证离线扫描先读取候选主键，再按主键更新仍然超时的节点，避免直接执行状态心跳范围更新。
     */
    @Test
    void shouldMarkOfflineNodesByCandidateIds() {
        log.info("测试离线扫描候选主键更新：模拟两个超时节点，预期仅按候选主键更新");
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.selectTimedOutNodeIds(
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"),
                eq(100))).thenReturn(List.of(11L, 12L));
        when(nodeMapper.markOfflineByIds(
                eq(List.of(11L, 12L)),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"))).thenReturn(2);

        service.markOfflineNodes();

        verify(nodeMapper).markOfflineByIds(
                eq(List.of(11L, 12L)),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"));
        log.info("离线扫描候选主键更新验证完成：candidateCount=2");
    }

    /**
     * 验证没有超时节点时立即结束扫描，不生成空 IN 条件，也不执行无意义更新。
     */
    @Test
    void shouldSkipUpdateWhenNoTimedOutNodeExists() {
        log.info("测试离线扫描空候选短路：模拟没有超时节点，预期不执行离线更新");
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.selectTimedOutNodeIds(
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"),
                eq(100))).thenReturn(List.of());

        service.markOfflineNodes();

        verify(nodeMapper, never()).markOfflineByIds(
                any(),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"));
        log.info("离线扫描空候选短路验证完成：离线更新调用次数=0");
    }

    /**
     * 验证锁冲突达到最大重试次数后由当前扫描周期收口，防止异常终止定时调度线程。
     */
    @Test
    void shouldSwallowDeadlockAfterMaxRetryToProtectScheduledThread() {
        log.info("测试离线扫描锁冲突上限：模拟连续三次死锁，预期异常不向调度线程传播");
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.selectTimedOutNodeIds(
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"),
                eq(100))).thenReturn(List.of(11L));
        when(nodeMapper.markOfflineByIds(
                eq(List.of(11L)),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007")))
                .thenThrow(new PessimisticLockingFailureException("deadlock"));

        service.markOfflineNodes();

        verify(nodeMapper, times(3)).markOfflineByIds(
                eq(List.of(11L)),
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"));
        log.info("离线扫描锁冲突上限验证完成：主键更新调用次数=3");
    }
}
