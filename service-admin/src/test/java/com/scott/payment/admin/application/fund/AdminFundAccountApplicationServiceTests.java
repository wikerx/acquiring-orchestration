package com.scott.payment.admin.application.fund;

import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundRechargeCreateRequest;
import com.scott.payment.admin.dto.fund.AdminFundAccountDTOs.FundDeductionCreateRequest;
import com.scott.payment.admin.service.AdminFundAccountService;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountApplicationServiceTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 资金账户应用服务身份边界测试，禁止缺失认证上下文时执行资金操作。
 * @status : create
 */
class AdminFundAccountApplicationServiceTests {

    /** 每个测试后清理线程认证上下文。 */
    @AfterEach
    void clearAuthenticationContext() {
        InternalAuthContextHolder.clear();
    }

    /** 缺少认证账号时必须拒绝创建充值，不能静默降级为 admin。 */
    @Test
    void shouldRejectRechargeWhenAuthenticationContextIsMissing() {
        AdminFundAccountService accountService = mock(AdminFundAccountService.class);
        AdminFundAccountApplicationService applicationService = new AdminFundAccountApplicationService(
                accountService,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class));

        assertThatThrownBy(() -> applicationService.createRecharge(new FundRechargeCreateRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("登录账号上下文缺失");
        verify(accountService, never()).createRecharge(any(), any(), any(), any());
    }

    /** 缺少认证账号时必须拒绝创建账户扣减，不能产生匿名资金申请。 */
    @Test
    void shouldRejectDeductionWhenAuthenticationContextIsMissing() {
        AdminFundAccountService accountService = mock(AdminFundAccountService.class);
        AdminFundAccountApplicationService applicationService = new AdminFundAccountApplicationService(
                accountService,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class));

        assertThatThrownBy(() -> applicationService.createDeduction(new FundDeductionCreateRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("登录账号上下文缺失");
        verify(accountService, never()).createDeduction(any(), any(), any(), any());
    }
}
