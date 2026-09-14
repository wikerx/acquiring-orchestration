package com.scott.payment.admin.application.monitor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorOnlineApplicationServiceTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 在线用户分页过滤、账号关联和会话查询条件应用服务测试。
 * @status : create
 */
class AdminMonitorOnlineApplicationServiceTest {

    @Test
    void shouldApplyIpAccountAndRealNameFiltersBeforePaging() {
        initializeTableInfo(SysLoginSessionDO.class, SysAccountDO.class, SysUserDO.class);
        SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
        SysAccountMapper accountMapper = mock(SysAccountMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);

        SysAccountDO account = new SysAccountDO();
        account.setId(11L);
        SysUserDO user = new SysUserDO();
        user.setId(22L);
        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        when(userMapper.selectList(any())).thenReturn(List.of(user));

        AtomicReference<Page<SysLoginSessionDO>> capturedPage = new AtomicReference<>();
        AtomicReference<LambdaQueryWrapper<SysLoginSessionDO>> capturedWrapper = new AtomicReference<>();
        doAnswer(invocation -> {
            Page<SysLoginSessionDO> page = invocation.getArgument(0);
            capturedPage.set(page);
            capturedWrapper.set(invocation.getArgument(1));
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        }).when(sessionMapper).selectPage(any(), any());

        AdminMonitorOnlineApplicationService service = new AdminMonitorOnlineApplicationService(
                sessionMapper, accountMapper, userMapper);

        service.pageOnlineUsers(2, 20, " 10.0.0 ", " alice ");

        assertThat(capturedPage.get()).isNotNull();
        assertThat(capturedPage.get().getCurrent()).isEqualTo(2);
        assertThat(capturedPage.get().getSize()).isEqualTo(20);
        assertThat(capturedWrapper.get()).isNotNull();
        assertThat(capturedWrapper.get().getSqlSegment())
                .containsIgnoringCase("logout")
                .containsIgnoringCase("expire_at")
                .containsIgnoringCase("login_ip LIKE")
                .containsIgnoringCase("account_id IN")
                .containsIgnoringCase("user_id IN")
                .containsIgnoringCase("ORDER BY created_at DESC");
    }

    @SafeVarargs
    private final void initializeTableInfo(Class<?>... entityTypes) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entityType : entityTypes) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace(entityType.getName());
            TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }
}
