package com.scott.payment.merchant.application.auth;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.ManagedImageReference;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.web.upload.ManagedImageStore;
import com.scott.payment.component.web.upload.SafeImageProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

/** 头像只属于可信会话中的用户主体，不接受客户端 userId 或文件地址。 */
@Service
@DS(DataSourceName.MASTER)
public class MerchantAvatarApplicationService {
    private final SysUserMapper users;
    private final SafeImageProcessor images;
    private final ManagedImageStore storage;
    public MerchantAvatarApplicationService(SysUserMapper users, SafeImageProcessor images, ManagedImageStore storage) {
        this.users = users; this.images = images; this.storage = storage;
    }
    @Transactional(rollbackFor = Exception.class)
    public String replace(MultipartFile file) {
        InternalAuthAccount account = account();
        SysUserDO before = user(account);
        byte[] content = images.process(file, true);
        String key = storage.write("avatar/" + account.getUserId(), content);
        change(account, before, key);
        storage.deleteAfterCommit(before.getAvatarUrl());
        return ManagedImageReference.version(key);
    }
    @Transactional(rollbackFor = Exception.class)
    public void remove() {
        InternalAuthAccount account = account();
        SysUserDO before = user(account);
        if (before.getAvatarUrl() == null) return;
        change(account, before, null);
        storage.deleteAfterCommit(before.getAvatarUrl());
    }
    public byte[] read() {
        InternalAuthAccount account = account();
        return storage.read(user(account).getAvatarUrl(), "avatar/" + account.getUserId());
    }
    private void change(InternalAuthAccount account, SysUserDO before, String key) {
        var update = Wrappers.<SysUserDO>lambdaUpdate().eq(SysUserDO::getId, account.getUserId())
                .eq(SysUserDO::getStatus, 1).eq(SysUserDO::getDeleted, 0L)
                .eq(before.getAvatarUrl() != null, SysUserDO::getAvatarUrl, before.getAvatarUrl())
                .isNull(before.getAvatarUrl() == null, SysUserDO::getAvatarUrl)
                .set(SysUserDO::getAvatarUrl, key).set(SysUserDO::getUpdatedAt, LocalDateTime.now())
                .set(SysUserDO::getUpdatedBy, account.getAccountId());
        if (users.update(null, update) != 1) throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "头像已被更新，请刷新后重试");
    }
    private SysUserDO user(InternalAuthAccount account) {
        SysUserDO user = users.selectById(account.getUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || !Long.valueOf(0).equals(user.getDeleted())) throw ManagedImageStore.missing();
        return user;
    }
    private InternalAuthAccount account() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !"MERCHANT".equals(account.getAppCode()) || account.getUserId() == null
                || account.getAccountId() == null || account.getMerchantId() == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        return account;
    }
}
