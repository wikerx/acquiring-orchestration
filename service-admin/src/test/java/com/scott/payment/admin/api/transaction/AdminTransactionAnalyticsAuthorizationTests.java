package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionAnalyticsApplicationService;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionAnalyticsAuthorizationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理端交易分析权限边界测试，确保权限不足时请求不会进入统计应用服务和数据库查询链路。
 * @status : create
 */
class AdminTransactionAnalyticsAuthorizationTests {

    private static final String APP_CODE = "ADMIN";
    private static final String AUTHORIZATION = "Bearer denied-token";

    private AdminTransactionAnalyticsApplicationService applicationService;
    private InternalAuthChecker authChecker;
    private MockMvc mockMvc;

    /** 初始化带真实权限拦截器的独立 MVC 测试环境。 */
    @BeforeEach
    void setUp() {
        applicationService = mock(AdminTransactionAnalyticsApplicationService.class);
        authChecker = mock(InternalAuthChecker.class);
        mockMvc = standaloneSetup(new AdminTransactionAnalyticsController(applicationService))
                .addInterceptors(new InternalAuthInterceptor(APP_CODE, authChecker, List.of()))
                .build();
    }

    /**
     * 权限校验失败必须在 Controller 执行前返回 403，禁止触发对应统计查询。
     *
     * @param path       统计接口路径
     * @param permission 接口要求的独立权限编码
     * @throws Exception MVC 请求执行失败
     */
    @ParameterizedTest
    @MethodSource("analyticsEndpoints")
    void forbiddenRequestShouldNotReachAnalyticsQuery(String path, String permission) throws Exception {
        when(authChecker.check(APP_CODE, AUTHORIZATION, "POST", path, permission))
                .thenThrow(new ServiceException(ApiResultEnum.FORBIDDEN));

        mockMvc.perform(post(path)
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }

    private static Stream<Arguments> analyticsEndpoints() {
        return Stream.of(
                Arguments.of("/admin/transactions/analytics/overview", "transaction:analytics:overview"),
                Arguments.of("/admin/transactions/analytics/merchants", "transaction:analytics:merchants"),
                Arguments.of("/admin/transactions/analytics/failures", "transaction:analytics:failures"),
                Arguments.of("/admin/transactions/analytics/channels", "transaction:analytics:channels"),
                Arguments.of("/admin/transactions/analytics/three-ds", "transaction:analytics:three-ds")
        );
    }
}
