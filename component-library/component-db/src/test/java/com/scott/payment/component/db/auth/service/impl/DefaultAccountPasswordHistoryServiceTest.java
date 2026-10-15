package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountPasswordHistoryDO;
import com.scott.payment.component.db.auth.mapper.SysAccountPasswordHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 最近密码历史策略的持久化与复用防护测试。 */
class DefaultAccountPasswordHistoryServiceTest {

    private final SysAccountPasswordHistoryMapper historyMapper = mock(SysAccountPasswordHistoryMapper.class);
    private DefaultAccountPasswordHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAccountPasswordHistoryService(historyMapper);
    }

    @Test
    void shouldRejectCurrentAndRecentHistoricalPasswords() {
        SysAccountDO account = accountWithPassword("CurrentAccess@2026");
        when(historyMapper.selectList(any())).thenReturn(List.of(history("EarlierAccess@2025")));

        assertThatThrownBy(() -> service.assertNotRecentlyUsed(account, "CurrentAccess@2026"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("new password was used recently");
        assertThatThrownBy(() -> service.assertNotRecentlyUsed(account, "EarlierAccess@2025"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("new password was used recently");
    }

    @Test
    void shouldPersistOnlyPreviousPasswordHashMaterial() {
        SysAccountDO account = accountWithPassword("CurrentAccess@2026");
        LocalDateTime changedAt = LocalDateTime.now();

        service.recordCurrentPassword(account, changedAt);

        ArgumentCaptor<SysAccountPasswordHistoryDO> captor =
                ArgumentCaptor.forClass(SysAccountPasswordHistoryDO.class);
        verify(historyMapper).insert(captor.capture());
        SysAccountPasswordHistoryDO row = captor.getValue();
        assertThat(row.getAppId()).isEqualTo(1L);
        assertThat(row.getAccountId()).isEqualTo(10L);
        assertThat(row.getPasswordHash()).isEqualTo(account.getPasswordHash());
        assertThat(row.getPasswordSalt()).isEqualTo(account.getPasswordSalt());
        assertThat(row.getPasswordAlgo()).isEqualTo(PasswordHashUtils.ALGORITHM);
        assertThat(row.getChangedAt()).isEqualTo(changedAt);
        assertThat(row.toString()).doesNotContain("CurrentAccess@2026");
    }

    private SysAccountDO accountWithPassword(String password) {
        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setId(10L);
        account.setAppId(1L);
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(password, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        return account;
    }

    private SysAccountPasswordHistoryDO history(String password) {
        String salt = PasswordHashUtils.generateSalt();
        SysAccountPasswordHistoryDO row = new SysAccountPasswordHistoryDO();
        row.setPasswordSalt(salt);
        row.setPasswordHash(PasswordHashUtils.hashPassword(password, salt));
        row.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        return row;
    }
}
