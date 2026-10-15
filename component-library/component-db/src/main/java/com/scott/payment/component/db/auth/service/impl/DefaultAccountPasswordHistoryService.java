package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountPasswordHistoryDO;
import com.scott.payment.component.db.auth.mapper.SysAccountPasswordHistoryMapper;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultAccountPasswordHistoryService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 默认密码历史实现；仅加载最近四条哈希进行恒定算法比对，并在换密事务内归档旧哈希。
 * @status : create
 */
@Service
public class DefaultAccountPasswordHistoryService implements AccountPasswordHistoryService {

    /** PCI 密码复用防护要求检查的最近历史数量。 */
    private static final int HISTORY_CHECK_COUNT = 4;

    private final SysAccountPasswordHistoryMapper historyMapper;

    /** 创建密码历史服务。 */
    public DefaultAccountPasswordHistoryService(SysAccountPasswordHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    /** 校验当前及最近四个旧密码，错误消息不透露命中了哪一条历史。 */
    @Override
    public void assertNotRecentlyUsed(SysAccountDO account, String candidatePassword) {
        if (account == null || !StringUtils.hasText(candidatePassword)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "new password is invalid");
        }
        if (matches(candidatePassword, account.getPasswordSalt(), account.getPasswordHash())) {
            throw reusedPassword();
        }
        List<SysAccountPasswordHistoryDO> history = historyMapper.selectList(
                Wrappers.<SysAccountPasswordHistoryDO>lambdaQuery()
                        .eq(SysAccountPasswordHistoryDO::getAppId, account.getAppId())
                        .eq(SysAccountPasswordHistoryDO::getAccountId, account.getId())
                        .orderByDesc(SysAccountPasswordHistoryDO::getChangedAt)
                        .orderByDesc(SysAccountPasswordHistoryDO::getId)
                        .last("LIMIT " + HISTORY_CHECK_COUNT));
        if (history != null && history.stream().anyMatch(item ->
                matches(candidatePassword, item.getPasswordSalt(), item.getPasswordHash()))) {
            throw reusedPassword();
        }
    }

    /** 将即将被替换的密码哈希材料写入历史表。 */
    @Override
    public void recordCurrentPassword(SysAccountDO account, LocalDateTime changedAt) {
        if (account == null || account.getId() == null || !StringUtils.hasText(account.getPasswordHash())
                || !StringUtils.hasText(account.getPasswordSalt())) {
            return;
        }
        SysAccountPasswordHistoryDO latest = historyMapper.selectOne(
                Wrappers.<SysAccountPasswordHistoryDO>lambdaQuery()
                        .eq(SysAccountPasswordHistoryDO::getAppId, account.getAppId())
                        .eq(SysAccountPasswordHistoryDO::getAccountId, account.getId())
                        .orderByDesc(SysAccountPasswordHistoryDO::getChangedAt)
                        .orderByDesc(SysAccountPasswordHistoryDO::getId)
                        .last("LIMIT 1"));
        if (latest != null
                && account.getPasswordHash().equals(latest.getPasswordHash())
                && account.getPasswordSalt().equals(latest.getPasswordSalt())) {
            return;
        }
        SysAccountPasswordHistoryDO row = new SysAccountPasswordHistoryDO();
        row.setAppId(account.getAppId());
        row.setAccountId(account.getId());
        row.setPasswordHash(account.getPasswordHash());
        row.setPasswordSalt(account.getPasswordSalt());
        row.setPasswordAlgo(account.getPasswordAlgo());
        row.setChangedAt(changedAt);
        row.setCreatedAt(changedAt);
        historyMapper.insert(row);
    }

    private boolean matches(String candidatePassword, String salt, String hash) {
        return StringUtils.hasText(salt) && StringUtils.hasText(hash)
                && PasswordHashUtils.matches(candidatePassword, salt, hash);
    }

    private ServiceException reusedPassword() {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "new password was used recently");
    }
}
