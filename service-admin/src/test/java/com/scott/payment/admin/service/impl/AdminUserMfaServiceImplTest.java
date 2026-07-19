package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaServiceImplTest
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 管理服务测试，覆盖 OTP 安全审计日志面向管理端的操作人展示兜底规则。
 * @status : create
 */
class AdminUserMfaServiceImplTest {

    @Test
    void pageLogsShouldFallbackOperatorToTargetLoginAccountForSelfMfaEvents() {
        SysAppMapper sysAppMapper = mock(SysAppMapper.class);
        SysAccountMapper sysAccountMapper = mock(SysAccountMapper.class);
        SysAccountMfaLogMapper sysAccountMfaLogMapper = mock(SysAccountMfaLogMapper.class);
        AdminUserMfaServiceImpl service = new AdminUserMfaServiceImpl(
                sysAppMapper,
                sysAccountMapper,
                mock(SysAccountMfaMapper.class),
                mock(SysAccountMfaTokenMapper.class),
                sysAccountMfaLogMapper,
                mock(SysLoginSessionMapper.class),
                mock(AdminEmailService.class),
                mock(AdminConfigService.class)
        );
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysAccountMapper.selectById(10L)).thenReturn(adminAccount());
        Page<SysAccountMfaLogDO> page = new Page<>(1, 10);
        page.setRecords(List.of(selfMfaLog()));
        page.setTotal(1);
        when(sysAccountMfaLogMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<UserMfaLogResponse> result = service.pageLogs(new UserMfaLogQuery());

        assertThat(result.getRecords()).singleElement()
                .extracting(UserMfaLogResponse::getOperatorLoginAccount)
                .isEqualTo("admin");
    }

    private SysAppDO adminApp() {
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        app.setAppCode(AuthConstants.APP_ADMIN);
        app.setStatus(AuthConstants.ENABLED);
        app.setDeleted(0L);
        return app;
    }

    private SysAccountDO adminAccount() {
        SysAccountDO account = new SysAccountDO();
        account.setId(10L);
        account.setLoginAccount("admin");
        account.setStatus(AuthConstants.ENABLED);
        account.setDeleted(0L);
        return account;
    }

    private SysAccountMfaLogDO selfMfaLog() {
        SysAccountMfaLogDO log = new SysAccountMfaLogDO();
        log.setId(100L);
        log.setAppId(1L);
        log.setAccountId(10L);
        log.setActionType("LOGIN_VERIFY");
        log.setResult("SUCCESS");
        log.setBeforePolicy(AuthConstants.MFA_POLICY_REQUIRED);
        log.setBeforeStatus(AuthConstants.MFA_STATUS_ENABLED);
        log.setAfterPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        log.setAfterStatus(AuthConstants.MFA_STATUS_ENABLED);
        log.setEventTime(LocalDateTime.now());
        return log;
    }
}
