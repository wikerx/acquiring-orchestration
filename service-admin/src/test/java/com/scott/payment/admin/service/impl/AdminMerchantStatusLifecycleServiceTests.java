package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantStatusLifecycleServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证商户冻结和解冻的会话失效、管理员通知及商户语言选择副作用
 * @status : create
 */
class AdminMerchantStatusLifecycleServiceTests {

    @BeforeEach
    void setUp() {
        initializeTableInfo(SysAppDO.class);
        initializeTableInfo(SysAccountDO.class);
        initializeTableInfo(com.scott.payment.component.db.auth.entity.SysLoginSessionDO.class);
        initializeTableInfo(SysRoleDO.class);
        initializeTableInfo(SysMerchantUserRoleDO.class);
        initializeTableInfo(SysMerchantUserDO.class);
    }

    @Test
    void freezeShouldLogoutAllMerchantSessionsAndNotifyAdministratorInMerchantLocale() {
        SysAppMapper appMapper = mock(SysAppMapper.class);
        SysAccountMapper accountMapper = mock(SysAccountMapper.class);
        SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMerchantUserRoleMapper relationMapper = mock(SysMerchantUserRoleMapper.class);
        SysMerchantUserMapper merchantUserMapper = mock(SysMerchantUserMapper.class);
        AdminEmailService emailService = mock(AdminEmailService.class);
        AdminMerchantStatusLifecycleService service = new AdminMerchantStatusLifecycleService(
                appMapper, accountMapper, sessionMapper, roleMapper, relationMapper, merchantUserMapper, emailService);

        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode(AuthConstants.APP_MERCHANT);
        when(appMapper.selectOne(any())).thenReturn(app);
        SysAccountDO admin = new SysAccountDO();
        admin.setId(11L);
        admin.setEmail("admin@example.com");
        when(accountMapper.selectList(any())).thenReturn(List.of(admin));
        SysRoleDO role = new SysRoleDO();
        role.setId(21L);
        when(roleMapper.selectOne(any())).thenReturn(role);
        SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
        relation.setMerchantUserId(31L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setId(31L);
        merchantUser.setAccountId(11L);
        when(merchantUserMapper.selectList(any())).thenReturn(List.of(merchantUser));
        BaseMerchantInfoDO merchant = merchant("en-US");

        service.onStatusChanged(merchant, 2, LocalDateTime.of(2026, 8, 5, 12, 0));

        verify(sessionMapper).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTemplateCode()).isEqualTo("MERCHANT_FROZEN");
        assertThat(requestCaptor.getValue().getLocale()).isEqualTo("en-US");
        assertThat(requestCaptor.getValue().getToEmails()).containsExactly("admin@example.com");
    }

    @Test
    void unfreezeShouldNotifyAdministratorWithoutLoggingOutSessions() {
        SysAppMapper appMapper = mock(SysAppMapper.class);
        SysAccountMapper accountMapper = mock(SysAccountMapper.class);
        SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMerchantUserRoleMapper relationMapper = mock(SysMerchantUserRoleMapper.class);
        SysMerchantUserMapper merchantUserMapper = mock(SysMerchantUserMapper.class);
        AdminEmailService emailService = mock(AdminEmailService.class);
        AdminMerchantStatusLifecycleService service = new AdminMerchantStatusLifecycleService(
                appMapper, accountMapper, sessionMapper, roleMapper, relationMapper, merchantUserMapper, emailService);

        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode(AuthConstants.APP_MERCHANT);
        when(appMapper.selectOne(any())).thenReturn(app);
        SysAccountDO admin = new SysAccountDO();
        admin.setId(11L);
        admin.setEmail("admin@example.com");
        when(accountMapper.selectList(any())).thenReturn(List.of(admin));
        SysRoleDO role = new SysRoleDO();
        role.setId(21L);
        when(roleMapper.selectOne(any())).thenReturn(role);
        SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
        relation.setMerchantUserId(31L);
        when(relationMapper.selectList(any())).thenReturn(List.of(relation));
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setId(31L);
        merchantUser.setAccountId(11L);
        when(merchantUserMapper.selectList(any())).thenReturn(List.of(merchantUser));

        service.onStatusChanged(merchant("zh-CN"), 1, LocalDateTime.of(2026, 8, 5, 13, 0));

        verify(sessionMapper, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTemplateCode()).isEqualTo("MERCHANT_UNFROZEN");
        assertThat(requestCaptor.getValue().getLocale()).isEqualTo("zh-CN");
        assertThat(requestCaptor.getValue().getToEmails()).containsExactly("admin@example.com");
    }

    private BaseMerchantInfoDO merchant(String locale) {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(1L);
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("Example Merchant");
        merchant.setContactEmail("fallback@example.com");
        merchant.setDefaultLocale(locale);
        return merchant;
    }

    private void initializeTableInfo(Class<?> entityType) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
