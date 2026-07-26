package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.client.job.JobSchedulerInternalClient;
import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.converter.JobSchedulerConverterImpl;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.admin.dto.monitor.JobTaskSaveRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminJobSchedulerApplicationServiceTest
 * @date : 2026-06-19 23:36
 * @email : scott_x@163.com
 * @description : AdminJobSchedulerApplicationServiceTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class AdminJobSchedulerApplicationServiceTest {

    @Mock
    /**
     * job Scheduler Internal Client 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private JobSchedulerInternalClient jobSchedulerInternalClient;

    @Mock
    /**
     * excel Export Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelExportService excelExportService;

    @Mock
    /**
     * excel I18n Message Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelI18nMessageResolver excelI18nMessageResolver;

    @Mock
    /**
     * excel Locale Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private ExcelLocaleResolver excelLocaleResolver;

    /**
     * admin Job Scheduler Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private AdminJobSchedulerApplicationService adminJobSchedulerApplicationService;

    @BeforeEach
    void setUp() {
        adminJobSchedulerApplicationService = new AdminJobSchedulerApplicationService(
                jobSchedulerInternalClient,
                excelExportService,
                excelI18nMessageResolver,
                excelLocaleResolver,
                new JobSchedulerConverterImpl()
        );
    }

    @Test
    void shouldMapOperatorAndTrimOptionalFieldsWhenCreatingTask() {
        JobTaskSaveRequest request = buildSaveRequest();
        request.setDescription("  demo task  ");
        request.setParams("   ");
        request.setCronExpression(" 0/30 * * * * ? ");
        JobTaskResponse expected = new JobTaskResponse();
        when(jobSchedulerInternalClient.createTask(org.mockito.ArgumentMatchers.any(JobTaskRemoteSaveRequest.class)))
                .thenReturn(expected);

        JobTaskResponse actual = adminJobSchedulerApplicationService.createTask(request, " admin ");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<JobTaskRemoteSaveRequest> requestCaptor = ArgumentCaptor.forClass(JobTaskRemoteSaveRequest.class);
        verify(jobSchedulerInternalClient).createTask(requestCaptor.capture());
        JobTaskRemoteSaveRequest remoteRequest = requestCaptor.getValue();
        assertThat(remoteRequest.getOperator()).isEqualTo("admin");
        assertThat(remoteRequest.getDescription()).isEqualTo("demo task");
        assertThat(remoteRequest.getParams()).isNull();
        assertThat(remoteRequest.getCronExpression()).isEqualTo("0/30 * * * * ?");
        assertThat(remoteRequest.getJobCode()).isEqualTo("PAY_TIMEOUT_CLOSE");
    }

    @Test
    void shouldMapOperatorAndKeepBusinessFieldsWhenUpdatingTask() {
        JobTaskSaveRequest request = buildSaveRequest();
        request.setDescription(null);
        request.setParams("{\"closeWindow\":30}");
        request.setCronExpression("  ");
        JobTaskResponse expected = new JobTaskResponse();
        when(jobSchedulerInternalClient.updateTask(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.any(JobTaskRemoteSaveRequest.class)
        )).thenReturn(expected);

        JobTaskResponse actual = adminJobSchedulerApplicationService.updateTask(100L, request, "ops-user");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<JobTaskRemoteSaveRequest> requestCaptor = ArgumentCaptor.forClass(JobTaskRemoteSaveRequest.class);
        verify(jobSchedulerInternalClient).updateTask(org.mockito.ArgumentMatchers.eq(100L), requestCaptor.capture());
        JobTaskRemoteSaveRequest remoteRequest = requestCaptor.getValue();
        assertThat(remoteRequest.getOperator()).isEqualTo("ops-user");
        assertThat(remoteRequest.getDescription()).isNull();
        assertThat(remoteRequest.getParams()).isEqualTo("{\"closeWindow\":30}");
        assertThat(remoteRequest.getCronExpression()).isNull();
        assertThat(remoteRequest.getStatus()).isEqualTo("ENABLED");
    }

    /**
     * 构造用于测试的任务保存请求。
     *
     * @return 任务保存请求
     */
    private JobTaskSaveRequest buildSaveRequest() {
        JobTaskSaveRequest request = new JobTaskSaveRequest();
        request.setJobCode("PAY_TIMEOUT_CLOSE");
        request.setJobName("支付超时关单");
        request.setJobGroup("PAYMENT");
        request.setHandlerCode("paymentTimeoutCloseJob");
        request.setSchedulerMode("DISTRIBUTED");
        request.setTriggerMode("CRON");
        request.setMisfireStrategy("FIRE_ONCE");
        request.setTimeoutSeconds(120);
        request.setRetryCount(2);
        request.setRetryIntervalSeconds(30);
        request.setAllowConcurrent(0);
        request.setStatus("ENABLED");
        return request;
    }
}
