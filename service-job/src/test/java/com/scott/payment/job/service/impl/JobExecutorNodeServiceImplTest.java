package com.scott.payment.job.service.impl;

import com.scott.payment.job.mapper.SysJobExecutorNodeMapper;
import com.scott.payment.job.support.JobNodeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeServiceImplTest
 * @date : 2026-07-12 19:20
 * @email : scott_x@163.com
 * @description : 任务执行节点服务测试，验证节点离线扫描在心跳并发更新下遇到死锁时可以重试收敛。
 * @status : create
 */
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

    @Test
    void shouldRetryWhenMarkOfflineNodesMeetsDeadlock() {
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.markOffline(any(LocalDateTime.class), eq("service-job@127.0.0.1:8007"), eq(100)))
                .thenThrow(new PessimisticLockingFailureException("deadlock"))
                .thenReturn(2);

        service.markOfflineNodes();

        ArgumentCaptor<LocalDateTime> offlineBeforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(nodeMapper, times(2)).markOffline(
                offlineBeforeCaptor.capture(),
                eq("service-job@127.0.0.1:8007"),
                eq(100));
        assertThat(offlineBeforeCaptor.getValue()).isBefore(LocalDateTime.now());
    }

    @Test
    void shouldSwallowDeadlockAfterMaxRetryToProtectScheduledThread() {
        when(jobNodeContext.offlineSeconds()).thenReturn(30);
        when(jobNodeContext.nodeId()).thenReturn("service-job@127.0.0.1:8007");
        when(nodeMapper.markOffline(any(LocalDateTime.class), eq("service-job@127.0.0.1:8007"), eq(100)))
                .thenThrow(new PessimisticLockingFailureException("deadlock"));

        service.markOfflineNodes();

        verify(nodeMapper, times(3)).markOffline(
                any(LocalDateTime.class),
                eq("service-job@127.0.0.1:8007"),
                eq(100));
    }
}
