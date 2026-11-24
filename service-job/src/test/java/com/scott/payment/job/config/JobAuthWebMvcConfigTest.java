package com.scott.payment.job.config;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import com.scott.payment.job.api.internal.JobExecutorNodeInternalController;
import com.scott.payment.job.api.internal.JobRunLogInternalController;
import com.scott.payment.job.api.internal.JobTaskInternalController;
import com.scott.payment.job.application.JobExecutorNodeApplicationService;
import com.scott.payment.job.application.JobRunLogApplicationService;
import com.scott.payment.job.application.JobTaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Job 内部接口鉴权边界测试，确保后台导出只跳过管理员会话而不跳过服务签名。
 */
class JobAuthWebMvcConfigTest {

    /**
     * 导出专用入口必须命中 HMAC 拦截器，同时排除管理员浏览器会话拦截器。
     */
    @Test
    void shouldRequireHmacButNotBrowserSessionForRunLogExport() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setEnabled(false);
        JobAuthWebMvcConfig config = new JobAuthWebMvcConfig(
                mock(SystemAuthService.class), properties, mock(InternalRequestReplayGuard.class));
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        config.addInterceptors(registry);

        MappedInterceptor hmac = interceptor(registry.interceptors(), InternalServiceAuthInterceptor.class);
        MappedInterceptor browserSession = interceptor(registry.interceptors(), InternalAuthInterceptor.class);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        assertThat(hmac.matches("/internal/job/logs/export/search", pathMatcher)).isTrue();
        assertThat(browserSession.matches("/internal/job/logs/export/search", pathMatcher)).isFalse();
        assertThat(hmac.matches("/internal/job/logs/search", pathMatcher)).isTrue();
        assertThat(browserSession.matches("/internal/job/logs/search", pathMatcher)).isTrue();
    }

    @Test
    void taskReaderCanOpenTheSchedulerWithoutWildcardPermission() throws Exception {
        var auth = mock(SystemAuthService.class);
        var account = accountWithPermissions("monitor:job:list");
        allowAssignedPermissions(auth, account);
        var taskService = mock(JobTaskApplicationService.class);
        var logService = mock(JobRunLogApplicationService.class);
        MockMvc mvc = mvc(auth, taskService, logService);

        mvc.perform(get("/internal/job/tasks/handlers").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/job/tasks/search").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/job/logs/trend").header("Authorization", "Bearer fixture"))
                .andExpect(status().isOk());

        mvc.perform(post("/internal/job/logs/search").header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/job/nodes").header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/internal/job/tasks/22/trigger").header("Authorization", "Bearer fixture"))
                .andExpect(status().isForbidden());
        verify(taskService).listHandlers();
        verify(logService).executionTrend();
    }

    @Test
    void enablingAndDisablingATaskRequireSeparatePermissions() throws Exception {
        var auth = mock(SystemAuthService.class);
        allowAssignedPermissions(auth, accountWithPermissions("monitor:job:list", "monitor:job:start"));
        var taskService = mock(JobTaskApplicationService.class);
        MockMvc mvc = mvc(auth, taskService, mock(JobRunLogApplicationService.class));

        mvc.perform(put("/internal/job/tasks/22/status").header("Authorization", "Bearer fixture")
                        .param("status", "DISABLED").param("operator", "fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResultEnum.FORBIDDEN.getCode()));
        verify(taskService, never()).changeStatus(22L, "DISABLED", "fixture");

        mvc.perform(put("/internal/job/tasks/22/status").header("Authorization", "Bearer fixture")
                        .param("status", "ENABLED").param("operator", "fixture"))
                .andExpect(status().isOk());
        verify(taskService).changeStatus(22L, "ENABLED", "fixture");
    }

    private MockMvc mvc(SystemAuthService auth, JobTaskApplicationService tasks, JobRunLogApplicationService logs) {
        return MockMvcBuilders.standaloneSetup(new JobTaskInternalController(tasks), new JobRunLogInternalController(logs),
                        new JobExecutorNodeInternalController(mock(JobExecutorNodeApplicationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new InternalAuthInterceptor("ADMIN", auth, List.of()))
                .build();
    }

    private void allowAssignedPermissions(SystemAuthService auth, InternalAuthAccount account) {
        when(auth.check(anyString(), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(invocation -> {
                    String required = invocation.getArgument(4);
                    if (!account.getPermissions().contains(required)) {
                        throw new ServiceException(ApiResultEnum.FORBIDDEN);
                    }
                    return account;
                });
    }

    private InternalAuthAccount accountWithPermissions(String... permissions) {
        var account = new InternalAuthAccount();
        account.setPermissions(List.of(permissions));
        return account;
    }

    /**
     * 按实际拦截器类型取得 Spring 生成的路径映射包装器。
     */
    private MappedInterceptor interceptor(List<Object> interceptors, Class<?> type) {
        return interceptors.stream()
                .map(MappedInterceptor.class::cast)
                .filter(item -> type.isInstance(item.getInterceptor()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 暴露 Spring 受保护的拦截器注册结果，仅用于验证路径包含和排除规则。
     */
    private static final class InspectableInterceptorRegistry extends InterceptorRegistry {
        private List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
