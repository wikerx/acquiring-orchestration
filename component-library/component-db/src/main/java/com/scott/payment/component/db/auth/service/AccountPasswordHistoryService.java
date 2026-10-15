package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.entity.SysAccountDO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AccountPasswordHistoryService
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 账号密码复用防护服务，统一校验当前密码和最近四条历史，并冻结被替换的旧哈希材料。
 * @status : create
 */
public interface AccountPasswordHistoryService {

    /**
     * 拒绝与当前密码或最近四条历史密码相同的候选密码。
     *
     * @param account 账号及当前哈希材料
     * @param candidatePassword 候选密码明文，仅用于本次哈希比对
     */
    void assertNotRecentlyUsed(SysAccountDO account, String candidatePassword);

    /**
     * 在覆盖账号密码前保存当前哈希材料。
     *
     * @param account 即将替换密码的账号
     * @param changedAt 密码被替换的时刻
     */
    void recordCurrentPassword(SysAccountDO account, LocalDateTime changedAt);
}
