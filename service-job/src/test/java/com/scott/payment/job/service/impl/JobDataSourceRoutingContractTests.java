package com.scott.payment.job.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.api.internal.dto.JobTaskQueryRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobDataSourceRoutingContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 固定任务管理普通查询与调度强一致查询的数据源路由边界。
 * @status : create
 */
class JobDataSourceRoutingContractTests {

    /**
     * 验证任务页面查询使用从库，降低管理查询对主库的占用。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteJobManagementQueriesToSlave() throws NoSuchMethodException {
        assertDataSource(JobTaskServiceImpl.class.getMethod("pageTasks", JobTaskQueryRequest.class),
                DataSourceName.SLAVE);
        assertDataSource(JobRunLogServiceImpl.class.getMethod("pageLogs", JobRunLogQueryRequest.class),
                DataSourceName.SLAVE);
        assertDataSource(JobRunLogServiceImpl.class.getMethod("listLogs", JobRunLogQueryRequest.class),
                DataSourceName.SLAVE);
        assertDataSource(JobExecutorNodeServiceImpl.class.getMethod("listNodes"), DataSourceName.SLAVE);
    }

    /**
     * 验证任务抢占和超时收敛前置查询固定读取主库，避免复制延迟造成重复调度。
     *
     * @throws NoSuchMethodException 方法签名变化时由测试显式失败
     */
    @Test
    void shouldRouteSchedulerConsistencyQueriesToMaster() throws NoSuchMethodException {
        assertDataSource(JobTaskServiceImpl.class.getMethod("getRequiredTask", Long.class),
                DataSourceName.MASTER);
        assertDataSource(JobTaskServiceImpl.class.getMethod(
                "selectDueTasks", LocalDateTime.class, int.class), DataSourceName.MASTER);
        assertDataSource(JobRunLogServiceImpl.class.getMethod("selectTimeoutCandidates"),
                DataSourceName.MASTER);
    }

    /**
     * 断言指定公开方法声明预期的数据源路由。
     *
     * @param method   被验证的公开方法
     * @param expected 预期数据源名称
     */
    private void assertDataSource(Method method, String expected) {
        DS dataSource = method.getAnnotation(DS.class);
        assertThat(dataSource)
                .as("method %s must declare a data source", method.getName())
                .isNotNull();
        assertThat(dataSource.value())
                .as("method %s data source", method.getName())
                .isEqualTo(expected);
    }
}
