package com.scott.payment.admin.application.channel;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.channel.ChannelPresentationDTOs.*;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.ManagedImageReference;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.web.upload.ManagedImageStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** 按调用页面的查看权限读取公开展示字段，保持原渠道配置和上传权限独立。 */
@Service
public class AdminChannelPresentationApplicationService {
    private static final int MAX_REFERENCES = 100;
    private final ChannelInfoMapper mapper;
    private final ManagedImageStore images;

    public AdminChannelPresentationApplicationService(ChannelInfoMapper mapper, ManagedImageStore images) {
        this.mapper = mapper;
        this.images = images;
    }

    /** 按已授权页面批量读取展示投影，禁止空条件扫描和返回渠道配置。 */
    @DS(DataSourceName.MASTER)
    public List<Presentation> lookup(Scope scope, LookupRequest request) {
        authorize(scope);
        if (request == null) throw invalid();
        List<Long> ids = request.ids() == null ? List.of() : request.ids();
        List<String> codes = request.codes() == null ? List.of() : request.codes();
        if (ids.size() + codes.size() > MAX_REFERENCES
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || codes.stream().anyMatch(code -> code == null || code.isBlank() || code.length() > 64
                    || code.chars().anyMatch(Character::isISOControl))) throw invalid();
        if (ids.isEmpty() && codes.isEmpty()) return List.of();
        var query = displayQuery().and(reference -> {
            if (!ids.isEmpty()) reference.in(ChannelInfoDO::getId, ids);
            if (!ids.isEmpty() && !codes.isEmpty()) reference.or();
            if (!codes.isEmpty()) reference.in(ChannelInfoDO::getChannelCode, codes);
        });
        return mapper.selectList(query).stream().map(channel -> new Presentation(channel.getId(),
                channel.getChannelCode(), channel.getChannelCnName(), channel.getChannelEnName(),
                ManagedImageReference.version(channel.getLogoObjectKey()))).toList();
    }

    /** 版本必须仍属于当前渠道，读取始终复用私有图片存储的归属校验。 */
    @DS(DataSourceName.MASTER)
    public byte[] logo(Scope scope, Long id, String version) {
        authorize(scope);
        if (id == null || id <= 0 || version == null || !version.matches("[a-f0-9]{32}")) throw invalid();
        var channel = mapper.selectOne(displayQuery().eq(ChannelInfoDO::getId, id));
        // 元数据和图片之间发生更换时拒绝旧版本，避免把新图片缓存到旧版本名下。
        if (channel == null || !Objects.equals(version, ManagedImageReference.version(channel.getLogoObjectKey()))) {
            throw ManagedImageStore.missing();
        }
        return images.read(channel.getLogoObjectKey(), "channel/" + id);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelInfoDO> displayQuery() {
        return Wrappers.<ChannelInfoDO>lambdaQuery()
                .select(ChannelInfoDO::getId, ChannelInfoDO::getChannelCode, ChannelInfoDO::getChannelCnName,
                        ChannelInfoDO::getChannelEnName, ChannelInfoDO::getLogoObjectKey)
                .eq(ChannelInfoDO::getDeleted, 0L);
    }

    private void authorize(Scope scope) {
        var account = InternalAuthContextHolder.get();
        if (account == null || account.getUserId() == null || account.getAccountId() == null
                || !"ADMIN".equals(account.getAppCode()) || scope == null || account.getPermissions() == null
                || !(account.getPermissions().contains("*:*:*")
                || account.getPermissions().contains(scope.listPermission())
                || account.getPermissions().contains(scope.detailPermission()))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "无权查看渠道展示信息");
        }
    }

    private ServiceException invalid() {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "渠道展示查询参数不合法或超过单次限制");
    }
}
