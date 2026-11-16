package com.scott.payment.merchant.application.system;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.AssetDO;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.TemplateDO;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.VersionDO;
import com.scott.payment.merchant.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.merchant.infrastructure.storage.TemplateObjectStorageService;
import com.scott.payment.merchant.mapper.MerchantFileTemplateAssetMapper;
import com.scott.payment.merchant.mapper.MerchantFileTemplateMapper;
import com.scott.payment.merchant.mapper.MerchantFileTemplateVersionMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 商户系统按稳定模板编码下载管理端已发布的商户或共享模板。 */
@Service
public class MerchantFileTemplateApplicationService {

    private final MerchantFileTemplateMapper templateMapper;
    private final MerchantFileTemplateVersionMapper versionMapper;
    private final MerchantFileTemplateAssetMapper assetMapper;
    private final ObjectProvider<TemplateObjectStorageService> storageProvider;
    private final ObjectStorageProperties storageProperties;

    public MerchantFileTemplateApplicationService(MerchantFileTemplateMapper templateMapper,
                                                  MerchantFileTemplateVersionMapper versionMapper,
                                                  MerchantFileTemplateAssetMapper assetMapper,
                                                  ObjectProvider<TemplateObjectStorageService> storageProvider,
                                                  ObjectStorageProperties storageProperties) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.assetMapper = assetMapper;
        this.storageProvider = storageProvider;
        this.storageProperties = storageProperties;
    }

    @DS(DataSourceName.MASTER)
    public Download download(String templateCode, String locale) {
        InternalAuthAccount account = requireAccount();
        String code = normalizeCode(templateCode);
        TemplateDO template = templateMapper.selectOne(Wrappers.<TemplateDO>lambdaQuery()
                .eq(TemplateDO::getTemplateCode, code)
                .in(TemplateDO::getOwnerSystem, List.of("MERCHANT", "SHARED"))
                .eq(TemplateDO::getStatus, 1)
                .eq(TemplateDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (template == null || template.getCurrentVersionId() == null) {
            throw notFound();
        }
        enforceTemplatePermission(account, template.getMerchantPermissionCode());
        VersionDO version = versionMapper.selectById(template.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getTemplateId(), template.getId())
                || !"PUBLISHED".equals(version.getPublishStatus())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板发布指针异常");
        }
        String normalizedLocale = normalizeLocale(locale);
        AssetDO asset = findAsset(version.getId(), normalizedLocale);
        if (asset == null) {
            asset = findAsset(version.getId(), "zh-CN");
        }
        if (asset == null) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板当前版本缺少可下载文件");
        }
        byte[] content = requireStorage().get(asset.getObjectKey());
        if (!MessageDigest.isEqual(asset.getSha256().getBytes(StandardCharsets.US_ASCII),
                sha256(content).getBytes(StandardCharsets.US_ASCII))) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板文件完整性校验失败");
        }
        String baseName = "en-US".equals(normalizedLocale) ? template.getNameEn() : template.getNameZh();
        return new Download(baseName + "." + template.getFileExtension(), asset.getContentType(), content);
    }

    private AssetDO findAsset(Long versionId, String locale) {
        return assetMapper.selectOne(Wrappers.<AssetDO>lambdaQuery()
                .eq(AssetDO::getTemplateVersionId, versionId)
                .eq(AssetDO::getLocale, locale)
                .last("LIMIT 1"));
    }

    private void enforceTemplatePermission(InternalAuthAccount account, String requiredPermission) {
        if (!StringUtils.hasText(requiredPermission)) {
            return;
        }
        List<String> permissions = account.getPermissions() == null ? List.of() : account.getPermissions();
        if (!permissions.contains("*:*:*") && !permissions.contains(requiredPermission.trim())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "无权下载该模板");
        }
    }

    private InternalAuthAccount requireAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "商户登录上下文缺失");
        }
        return account;
    }

    private TemplateObjectStorageService requireStorage() {
        TemplateObjectStorageService storage = storageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "系统模板对象存储未启用");
        }
        return storage;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "模板编码不能为空");
        }
        String value = code.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "模板编码格式不正确");
        }
        return value;
    }

    private String normalizeLocale(String locale) {
        return StringUtils.hasText(locale) && locale.trim().toLowerCase(Locale.ROOT).startsWith("en")
                ? "en-US" : "zh-CN";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ServiceException notFound() {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "模板未发布或不属于商户系统");
    }

    public record Download(String filename, String contentType, byte[] content) {
    }
}
