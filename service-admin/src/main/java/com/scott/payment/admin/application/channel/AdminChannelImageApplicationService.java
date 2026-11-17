package com.scott.payment.admin.application.channel;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.channel.ChannelDTOs.*;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.service.AdminChannelService;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.web.upload.ManagedImageStore;
import com.scott.payment.component.web.upload.SafeImageProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 渠道资料与图片在同一数据库事务保存，失败时保留原图。 */
@Service
public class AdminChannelImageApplicationService {
    private final AdminChannelService channels;
    private final ChannelInfoMapper mapper;
    private final SafeImageProcessor images;
    private final ManagedImageStore storage;
    public AdminChannelImageApplicationService(AdminChannelService channels, ChannelInfoMapper mapper,
            SafeImageProcessor images, ManagedImageStore storage) {
        this.channels = channels; this.mapper = mapper; this.images = images; this.storage = storage;
    }
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public ChannelInfoResponse save(Long id, ChannelInfoSaveRequest request, MultipartFile file, boolean removeLogo) {
        authorize(id == null ? "channel:info:add" : "channel:info:edit");
        ChannelInfoDO before = id == null ? null : existing(id, true);
        if (file != null && removeLogo) throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "不能同时上传和移除渠道 Logo");
        byte[] content = file == null ? null : images.process(file, false);
        ChannelInfoResponse saved = id == null ? channels.createChannel(request) : channels.updateChannel(id, request);
        if (content != null || removeLogo) {
            String key = content == null ? null : storage.write("channel/" + saved.getId(), content);
            if (mapper.update(null, Wrappers.<ChannelInfoDO>lambdaUpdate().eq(ChannelInfoDO::getId, saved.getId())
                    .eq(ChannelInfoDO::getDeleted, 0L).set(ChannelInfoDO::getLogoObjectKey, key)) != 1) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "渠道已变更，请刷新后重试");
            }
            if (before != null) storage.deleteAfterCommit(before.getLogoObjectKey());
        }
        return channels.getChannel(saved.getId());
    }
    @DS(DataSourceName.MASTER)
    public byte[] read(Long id) {
        authorize("channel:info:list");
        return storage.read(existing(id, false).getLogoObjectKey(), "channel/" + id);
    }
    @Transactional(rollbackFor = Exception.class)
    @DS(DataSourceName.MASTER)
    public void delete(Long id) {
        authorize("channel:info:remove");
        ChannelInfoDO before = existing(id, true);
        channels.deleteChannel(id);
        storage.deleteAfterCommit(before.getLogoObjectKey());
    }
    private ChannelInfoDO existing(Long id, boolean lock) {
        var query = Wrappers.<ChannelInfoDO>lambdaQuery().eq(ChannelInfoDO::getId, id).eq(ChannelInfoDO::getDeleted, 0L);
        if (lock) query.last("FOR UPDATE");
        ChannelInfoDO entity = mapper.selectOne(query);
        if (entity == null) throw ManagedImageStore.missing();
        return entity;
    }
    private void authorize(String permission) {
        var account = InternalAuthContextHolder.get();
        if (account == null || !"ADMIN".equals(account.getAppCode()) || account.getPermissions() == null
                || !(account.getPermissions().contains("*:*:*") || account.getPermissions().contains(permission))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "无权操作渠道图片");
        }
    }
}
