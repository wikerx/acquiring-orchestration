package com.scott.payment.admin.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminAuthApplicationServiceTest
 * @date : 2026-06-19 19:12
 * @email : scott_x@163.com
 * @description : Admin Auth Application Service Test 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
class AdminAuthApplicationServiceTest {

    @Mock
    /**
     * system Auth Service 依赖，用于 Admin Auth Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SystemAuthService systemAuthService;

    @Mock
    /**
     * servlet Request，用于保存 Admin Auth Application Service Test 中与 servletrequest 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private HttpServletRequest servletRequest;

    /**
     * admin Auth Application Service 依赖，用于 Admin Auth Application Service Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private AdminAuthApplicationService adminAuthApplicationService;

    @BeforeEach
    void setUp() {
        adminAuthApplicationService = new AdminAuthApplicationService(systemAuthService);
    }

    @Test
    void shouldDelegateRegisterToSystemAuthService() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        AuthAccountDTO expected = new AuthAccountDTO();
        when(systemAuthService.register(AuthConstants.APP_ADMIN, request)).thenReturn(expected);

        AuthAccountDTO actual = adminAuthApplicationService.register(request);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).register(AuthConstants.APP_ADMIN, request);
    }

    @Test
    void shouldUseForwardedIpWhenSendingVerifyCode() {
        AuthVerifyCodeSendRequest request = new AuthVerifyCodeSendRequest();
        AuthVerifyCodeSendResponse expected = new AuthVerifyCodeSendResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2");
        when(systemAuthService.sendLoginVerifyCode(AuthConstants.APP_ADMIN, request, "1.1.1.1"))
                .thenReturn(expected);

        AuthVerifyCodeSendResponse actual = adminAuthApplicationService.sendVerifyCode(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).sendLoginVerifyCode(AuthConstants.APP_ADMIN, request, "1.1.1.1");
    }

    @Test
    void shouldUseRemoteAddressAndUserAgentWhenLoggingIn() {
        AuthLoginRequest request = new AuthLoginRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("admin-browser");
        when(systemAuthService.login(AuthConstants.APP_ADMIN, request, "127.0.0.1", "admin-browser"))
                .thenReturn(expected);

        AuthLoginResponse actual = adminAuthApplicationService.login(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).login(AuthConstants.APP_ADMIN, request, "127.0.0.1", "admin-browser");
    }

    @Test
    void shouldDelegateCurrentUserAndLogout() {
        AuthLoginResponse expected = new AuthLoginResponse();
        when(systemAuthService.currentUser(AuthConstants.APP_ADMIN, "Bearer token")).thenReturn(expected);

        AuthLoginResponse actual = adminAuthApplicationService.currentUser("Bearer token");
        adminAuthApplicationService.logout("Bearer token");

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).currentUser(AuthConstants.APP_ADMIN, "Bearer token");
        verify(systemAuthService).logout(AuthConstants.APP_ADMIN, "Bearer token");
    }

    @Test
    void shouldDelegateProfileUpdateAndPasswordChange() {
        AuthProfileUpdateRequest profileRequest = new AuthProfileUpdateRequest();
        AuthPasswordChangeRequest passwordRequest = new AuthPasswordChangeRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(systemAuthService.updateCurrentProfile(AuthConstants.APP_ADMIN, "Bearer token", profileRequest))
                .thenReturn(expected);

        AuthLoginResponse actual = adminAuthApplicationService.updateCurrentProfile("Bearer token", profileRequest);
        adminAuthApplicationService.changeCurrentPassword("Bearer token", passwordRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).updateCurrentProfile(AuthConstants.APP_ADMIN, "Bearer token", profileRequest);
        verify(systemAuthService).changeCurrentPassword(AuthConstants.APP_ADMIN, "Bearer token", passwordRequest);
    }
}
