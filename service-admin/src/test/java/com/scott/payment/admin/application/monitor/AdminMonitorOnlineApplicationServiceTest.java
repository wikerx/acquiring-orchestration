package com.scott.payment.admin.application.monitor;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.monitor.OnlineUserPageResponse;
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
 * 在线用户分页过滤、账号关联和会话查询条件应用服务测试。
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

        OnlineUserPageResponse response = service.pageOnlineUsers(2, 20, " 10.0.0 ", " alice ");

        assertThat(response.getRecords()).isEmpty();
        assertThat(response.getTotal()).isZero();
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

    /**
     * 在线会话主键必须在返回前转换为字符串，并优先展示用户真实姓名。
     */
    @Test
    void shouldReturnTypedOnlineUserAndPreserveBigintSessionId() {
        initializeTableInfo(SysLoginSessionDO.class, SysAccountDO.class, SysUserDO.class);
        SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
        SysAccountMapper accountMapper = mock(SysAccountMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);

        SysLoginSessionDO session = new SysLoginSessionDO();
        session.setId(9_007_199_254_740_993L);
        session.setAccountId(11L);
        session.setUserId(22L);
        session.setLoginIp("10.0.0.8");
        session.setCreatedAt(java.time.LocalDateTime.of(2026, 11, 1, 9, 30));
        session.setUserAgent("test-agent");
        doAnswer(invocation -> {
            Page<SysLoginSessionDO> page = invocation.getArgument(0);
            page.setRecords(List.of(session));
            page.setTotal(1L);
            return page;
        }).when(sessionMapper).selectPage(any(), any());

        SysAccountDO account = new SysAccountDO();
        account.setId(11L);
        account.setLoginAccount("alice-account");
        SysUserDO user = new SysUserDO();
        user.setId(22L);
        user.setRealName("Alice");
        when(accountMapper.selectByIds(List.of(11L))).thenReturn(List.of(account));
        when(userMapper.selectByIds(List.of(22L))).thenReturn(List.of(user));

        AdminMonitorOnlineApplicationService service = new AdminMonitorOnlineApplicationService(
                sessionMapper, accountMapper, userMapper);

        OnlineUserPageResponse response = service.pageOnlineUsers(1, 10, null, null);

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords()).singleElement().satisfies(onlineUser -> {
            assertThat(onlineUser.getSessionId()).isEqualTo("9007199254740993");
            assertThat(onlineUser.getUserName()).isEqualTo("Alice");
            assertThat(onlineUser.getLoginIp()).isEqualTo("10.0.0.8");
            assertThat(onlineUser.getUserAgent()).isEqualTo("test-agent");
        });
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
