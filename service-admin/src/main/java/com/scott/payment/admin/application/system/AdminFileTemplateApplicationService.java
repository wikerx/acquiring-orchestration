package com.scott.payment.admin.application.system;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.system.FileTemplateDTOs;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Asset;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.CreateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Detail;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Download;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.FormOptions;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.ModuleOption;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.PermissionOption;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.PublishRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Query;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Summary;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.UpdateRequest;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Version;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateAssetDO;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateDO;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateVersionDO;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.admin.infrastructure.storage.TemplateObjectStorageService;
import com.scott.payment.admin.mapper.SystemFileTemplateAssetMapper;
import com.scott.payment.admin.mapper.SystemFileTemplateMapper;
import com.scott.payment.admin.mapper.SystemFileTemplateVersionMapper;
import com.scott.payment.admin.support.template.TemplateFileSecurityValidator;
import com.scott.payment.admin.support.template.TemplateFileSecurityValidator.ValidatedFile;
import com.scott.payment.admin.support.template.BuiltinFileTemplateCatalog;
import com.scott.payment.admin.support.template.BuiltinFileTemplateCatalog.Spec;
import com.scott.payment.admin.support.template.FileTemplateModuleCatalog;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 管理端系统文件模板中心用例编排。
 *
 * <p>定义可维护、版本内容不可变、发布指针使用 CAS 更新；上传正文与数据库事务通过回滚补偿
 * 保持最终一致。任何响应都不暴露底层 Bucket、对象键或存储端点。</p>
 */
@Slf4j
@Service
public class AdminFileTemplateApplicationService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final Set<String> OWNER_SYSTEMS = Set.of("ADMIN", "MERCHANT", "SHARED");

    private final SystemFileTemplateMapper templateMapper;
    private final SystemFileTemplateVersionMapper versionMapper;
    private final SystemFileTemplateAssetMapper assetMapper;
    private final TemplateFileSecurityValidator fileValidator;
    private final ObjectProvider<TemplateObjectStorageService> storageProvider;
    private final ObjectStorageProperties storageProperties;
    private final GlobalIdGenerator globalIdGenerator;
    private final SysAppMapper appMapper;
    private final SysPermissionMapper permissionMapper;

    public AdminFileTemplateApplicationService(SystemFileTemplateMapper templateMapper,
                                               SystemFileTemplateVersionMapper versionMapper,
                                               SystemFileTemplateAssetMapper assetMapper,
                                               TemplateFileSecurityValidator fileValidator,
                                               ObjectProvider<TemplateObjectStorageService> storageProvider,
                                               ObjectStorageProperties storageProperties,
                                               GlobalIdGenerator globalIdGenerator,
                                               SysAppMapper appMapper,
                                               SysPermissionMapper permissionMapper) {
        this.templateMapper = templateMapper;
        this.versionMapper = versionMapper;
        this.assetMapper = assetMapper;
        this.fileValidator = fileValidator;
        this.storageProvider = storageProvider;
        this.storageProperties = storageProperties;
        this.globalIdGenerator = globalIdGenerator;
        this.appMapper = appMapper;
        this.permissionMapper = permissionMapper;
    }

    /** 分页查询模板定义。 */
    @DS(DataSourceName.SLAVE)
    public PageResult<Summary> page(Query request) {
        Query query = request == null ? new Query() : request;
        String ownerSystem = StringUtils.hasText(query.getOwnerSystem())
                ? normalizeOwnerSystem(query.getOwnerSystem())
                : null;
        var wrapper = Wrappers.<FileTemplateDO>lambdaQuery()
                .eq(FileTemplateDO::getDeleted, NOT_DELETED)
                .eq(ownerSystem != null, FileTemplateDO::getOwnerSystem, ownerSystem)
                .eq(query.getStatus() != null, FileTemplateDO::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(FileTemplateDO::getTemplateCode, query.getKeyword().trim())
                        .or().like(FileTemplateDO::getNameZh, query.getKeyword().trim())
                        .or().like(FileTemplateDO::getNameEn, query.getKeyword().trim())
                        .or().like(FileTemplateDO::getModuleCode, query.getKeyword().trim()))
                .orderByAsc(FileTemplateDO::getOwnerSystem, FileTemplateDO::getModuleCode,
                        FileTemplateDO::getTemplateCode);
        Page<FileTemplateDO> page = templateMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        List<Summary> records = page.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** 查询模板定义、版本和资产元数据。 */
    @DS(DataSourceName.MASTER)
    public Detail detail(Long id) {
        FileTemplateDO template = requireTemplate(id);
        Detail detail = toDetail(template);
        List<FileTemplateVersionDO> versions = versionMapper.selectList(Wrappers.<FileTemplateVersionDO>lambdaQuery()
                .eq(FileTemplateVersionDO::getTemplateId, template.getId())
                .orderByDesc(FileTemplateVersionDO::getVersionNo));
        detail.setVersions(versions.stream().map(this::toVersion).toList());
        return detail;
    }

    /** 按稳定编码查询模板，供系统内置目录幂等初始化使用。 */
    @DS(DataSourceName.MASTER)
    public Optional<Detail> findDetailByTemplateCode(String templateCode) {
        FileTemplateDO template = findByCode(normalizeTemplateCode(templateCode));
        return template == null ? Optional.empty() : Optional.of(detail(template.getId()));
    }

    /** 返回模板表单使用的后端受控模块与有效权限选项。 */
    @DS(DataSourceName.MASTER)
    public FormOptions formOptions() {
        FormOptions result = new FormOptions();
        result.setModules(FileTemplateModuleCatalog.all().stream().map(item -> {
            ModuleOption option = new ModuleOption();
            option.setCode(item.getCode());
            option.setNameZh(item.getNameZh());
            option.setNameEn(item.getNameEn());
            option.setOwnerSystems(item.getOwnerSystems().stream().sorted().toList());
            return option;
        }).toList());
        result.setAdminPermissions(permissionOptions("ADMIN"));
        result.setMerchantPermissions(permissionOptions("MERCHANT"));
        return result;
    }

    /** 创建稳定模板定义；相同编码和相同载荷的网络重试幂等返回原记录。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Detail create(CreateRequest request) {
        NormalizedDefinition normalized = normalizeDefinition(request.getOwnerSystem(), request.getModuleCode(),
                request.getNameZh(), request.getNameEn(), request.getAdminPermissionCode(),
                request.getMerchantPermissionCode(), request.getRemark());
        String extension = request.getFileExtension().trim().toLowerCase(Locale.ROOT);
        String code = resolveCreateCode(request.getTemplateCode(), normalized, extension);
        FileTemplateDO existing = findByCode(code);
        if (existing != null) {
            if (sameDefinition(existing, normalized, extension)) {
                return detail(existing.getId());
            }
            throw invalid("模板编码已存在且请求内容不一致");
        }
        LocalDateTime now = LocalDateTime.now();
        FileTemplateDO row = new FileTemplateDO();
        row.setTemplateCode(code);
        row.setOwnerSystem(normalized.ownerSystem());
        row.setModuleCode(normalized.moduleCode());
        row.setNameZh(normalized.nameZh());
        row.setNameEn(normalized.nameEn());
        row.setFileExtension(extension);
        row.setAdminPermissionCode(normalized.adminPermissionCode());
        row.setMerchantPermissionCode(normalized.merchantPermissionCode());
        row.setStatus(ENABLED);
        row.setRemark(normalized.remark());
        row.setVersion(0L);
        row.setCreateBy(operatorName());
        row.setCreateTime(now);
        row.setUpdateBy(operatorName());
        row.setUpdateTime(now);
        row.setDeleted(NOT_DELETED);
        try {
            templateMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            FileTemplateDO concurrent = findByCode(code);
            if (concurrent != null && sameDefinition(concurrent, normalized, extension)) {
                return detail(concurrent.getId());
            }
            throw invalid("模板编码已存在，请刷新后重试");
        }
        return detail(row.getId());
    }

    /** CAS 修改可变元数据，模板编码和扩展名保持稳定。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Detail update(Long id, UpdateRequest request) {
        requireTemplate(id);
        NormalizedDefinition normalized = normalizeDefinition(request.getOwnerSystem(), request.getModuleCode(),
                request.getNameZh(), request.getNameEn(), request.getAdminPermissionCode(),
                request.getMerchantPermissionCode(), request.getRemark());
        int affected = templateMapper.updateMetadataCas(id, normalized.ownerSystem(), normalized.moduleCode(),
                normalized.nameZh(), normalized.nameEn(), normalized.adminPermissionCode(),
                normalized.merchantPermissionCode(), request.getStatus(),
                normalized.remark(), request.getExpectedVersion(), operatorName(), LocalDateTime.now());
        if (affected != 1) {
            throw invalid("模板定义已被其他操作修改，请刷新后重试");
        }
        return detail(id);
    }

    /**
     * 上传包含中英文资产的不可变版本。
     *
     * <p>requestId 在同一模板内唯一，前端重复点击或网络重试不会产生重复版本。</p>
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Version uploadVersion(Long templateId, Integer versionNo, String schemaVersion, String requestId,
                                 String releaseNote, MultipartFile zhFile, MultipartFile enFile) {
        FileTemplateDO template = requireTemplate(templateId);
        String normalizedRequestId = normalizeRequestId(requestId);
        FileTemplateVersionDO retried = findVersionByRequestId(templateId, normalizedRequestId);
        if (retried != null) {
            return toVersion(retried);
        }
        if (versionNo == null || versionNo < 1) {
            throw invalid("版本号必须大于0");
        }
        FileTemplateVersionDO sameVersion = findVersion(templateId, versionNo);
        if (sameVersion != null) {
            throw invalid("该模板版本号已存在");
        }
        ValidatedFile zh = fileValidator.validate(zhFile, template.getFileExtension());
        ValidatedFile en = fileValidator.validate(enFile, template.getFileExtension());
        TemplateObjectStorageService storage = requireStorage();
        String zhKey = objectKey(template, versionNo, "zh-CN");
        String enKey = objectKey(template, versionNo, "en-US");
        storage.put(zhKey, zh.contentType(), zh.content());
        registerRollbackCleanup(storage, zhKey);
        storage.put(enKey, en.contentType(), en.content());
        registerRollbackCleanup(storage, enKey);

        LocalDateTime now = LocalDateTime.now();
        FileTemplateVersionDO version = new FileTemplateVersionDO();
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setSchemaVersion(trimToNull(schemaVersion));
        version.setRequestId(normalizedRequestId);
        version.setReleaseNote(trimToNull(releaseNote));
        version.setPublishStatus("DRAFT");
        version.setCreateBy(operatorName());
        version.setCreateTime(now);
        try {
            versionMapper.insert(version);
            assetMapper.insert(toAsset(version.getId(), "zh-CN", zh, zhKey, now));
            assetMapper.insert(toAsset(version.getId(), "en-US", en, enKey, now));
        } catch (DuplicateKeyException exception) {
            FileTemplateVersionDO concurrent = findVersionByRequestId(templateId, normalizedRequestId);
            if (concurrent != null) {
                return toVersion(concurrent);
            }
            throw invalid("模板版本号已存在，请刷新后重试");
        }
        return toVersion(version);
    }

    /** 首次发布草稿版本。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Detail publish(Long templateId, Long versionId, PublishRequest request) {
        return switchCurrentVersion(templateId, versionId, request, false);
    }

    /** 回滚到历史已发布版本。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public Detail rollback(Long templateId, Long versionId, PublishRequest request) {
        return switchCurrentVersion(templateId, versionId, request, true);
    }

    /** 按模板编码和语言下载当前已发布资产。 */
    @DS(DataSourceName.MASTER)
    public Download downloadCurrent(String templateCode, String locale) {
        String code = normalizeTemplateCode(templateCode);
        FileTemplateDO template = findByCode(code);
        if (template == null || !Objects.equals(template.getStatus(), ENABLED)
                || template.getCurrentVersionId() == null) {
            throw notFound("模板未发布或已停用");
        }
        return downloadPublished(template, locale);
    }

    /** 旧业务 URL 迁移期间优先读取模板中心；尚未登记或发布时允许旧生成器继续兜底。 */
    @DS(DataSourceName.MASTER)
    public Optional<Download> downloadCurrentIfPublished(String templateCode, String locale) {
        FileTemplateDO template = findByCode(normalizeTemplateCode(templateCode));
        if (template == null || !Objects.equals(template.getStatus(), ENABLED)
                || template.getCurrentVersionId() == null) {
            return Optional.empty();
        }
        return Optional.of(downloadPublished(template, locale));
    }

    private Download downloadPublished(FileTemplateDO template, String locale) {
        enforceTemplatePermission(requireAccount(), template.getAdminPermissionCode());
        FileTemplateVersionDO version = versionMapper.selectById(template.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getTemplateId(), template.getId())
                || !"PUBLISHED".equals(version.getPublishStatus())) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板发布指针异常");
        }
        String normalizedLocale = normalizeLocale(locale);
        FileTemplateAssetDO asset = findAsset(version.getId(), normalizedLocale);
        if (asset == null) {
            asset = findAsset(version.getId(), "zh-CN");
        }
        if (asset == null) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板当前版本缺少可下载文件");
        }
        byte[] content = requireStorage().get(asset.getObjectKey());
        if (!MessageDigest.isEqual(asset.getSha256().getBytes(StandardCharsets.US_ASCII),
                TemplateDigest.sha256(content).getBytes(StandardCharsets.US_ASCII))) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "模板文件完整性校验失败");
        }
        String baseName = "en-US".equals(normalizedLocale) ? template.getNameEn() : template.getNameZh();
        return new Download(baseName + "." + template.getFileExtension(), asset.getContentType(), content);
    }

    private Detail switchCurrentVersion(Long templateId, Long versionId, PublishRequest request, boolean rollback) {
        FileTemplateDO template = templateMapper.selectByIdForUpdate(templateId);
        if (template == null) {
            throw notFound("模板不存在");
        }
        FileTemplateVersionDO target = versionMapper.selectById(versionId);
        if (target == null || !Objects.equals(target.getTemplateId(), templateId)) {
            throw notFound("模板版本不存在");
        }
        if (Objects.equals(template.getLastOperationRequestId(), request.getRequestId())
                && Objects.equals(template.getCurrentVersionId(), versionId)) {
            return detail(templateId);
        }
        if (Objects.equals(template.getCurrentVersionId(), versionId)) {
            return detail(templateId);
        }
        if (rollback && !Set.of("PUBLISHED", "ARCHIVED").contains(target.getPublishStatus())) {
            throw invalid("只能回滚到曾经发布过的版本");
        }
        if (!rollback && !"DRAFT".equals(target.getPublishStatus())) {
            throw invalid("只能发布草稿版本");
        }
        requireTwoLocales(versionId);
        LocalDateTime now = LocalDateTime.now();
        if (template.getCurrentVersionId() != null) {
            FileTemplateVersionDO previous = versionMapper.selectById(template.getCurrentVersionId());
            if (previous != null) {
                previous.setPublishStatus("ARCHIVED");
                versionMapper.updateById(previous);
            }
        }
        target.setPublishStatus("PUBLISHED");
        target.setPublishedBy(operatorName());
        target.setPublishReason(request.getReason().trim());
        target.setPublishedTime(now);
        versionMapper.updateById(target);
        int affected = templateMapper.pointCurrentVersionCas(templateId, versionId, request.getRequestId().trim(),
                request.getExpectedTemplateVersion(), operatorName(), now);
        if (affected != 1) {
            throw invalid("模板发布状态已变化，请刷新后重试");
        }
        return detail(templateId);
    }

    private void requireTwoLocales(Long versionId) {
        List<FileTemplateAssetDO> assets = assets(versionId);
        Set<String> locales = assets.stream().map(FileTemplateAssetDO::getLocale).collect(java.util.stream.Collectors.toSet());
        if (!locales.containsAll(Set.of("zh-CN", "en-US"))) {
            throw invalid("发布版本必须同时包含中文和英文模板文件");
        }
    }

    private FileTemplateAssetDO toAsset(Long versionId, String locale, ValidatedFile file,
                                        String objectKey, LocalDateTime now) {
        FileTemplateAssetDO asset = new FileTemplateAssetDO();
        asset.setTemplateVersionId(versionId);
        asset.setLocale(locale);
        asset.setOriginalFilename(file.originalFilename());
        asset.setContentType(file.contentType());
        asset.setFileSize((long) file.content().length);
        asset.setSha256(file.sha256());
        asset.setStorageProvider(normalizedProvider());
        asset.setBucketName(storageProperties.getTemplateBucket());
        asset.setObjectKey(objectKey);
        asset.setCreateBy(operatorName());
        asset.setCreateTime(now);
        return asset;
    }

    private Summary toSummary(FileTemplateDO row) {
        Summary result = new Summary();
        copySummary(row, result);
        if (row.getCurrentVersionId() != null) {
            FileTemplateVersionDO current = versionMapper.selectById(row.getCurrentVersionId());
            result.setCurrentVersionNo(current == null ? null : current.getVersionNo());
        }
        return result;
    }

    private Detail toDetail(FileTemplateDO row) {
        Detail result = new Detail();
        copySummary(row, result);
        if (row.getCurrentVersionId() != null) {
            FileTemplateVersionDO current = versionMapper.selectById(row.getCurrentVersionId());
            result.setCurrentVersionNo(current == null ? null : current.getVersionNo());
        }
        result.setRemark(row.getRemark());
        result.setCreateBy(row.getCreateBy());
        result.setCreateTime(row.getCreateTime());
        return result;
    }

    private void copySummary(FileTemplateDO row, Summary result) {
        result.setId(row.getId());
        result.setTemplateCode(row.getTemplateCode());
        result.setOwnerSystem(row.getOwnerSystem());
        result.setModuleCode(row.getModuleCode());
        result.setNameZh(row.getNameZh());
        result.setNameEn(row.getNameEn());
        result.setFileExtension(row.getFileExtension());
        result.setAdminPermissionCode(row.getAdminPermissionCode());
        result.setMerchantPermissionCode(row.getMerchantPermissionCode());
        result.setStatus(row.getStatus());
        result.setCurrentVersionId(row.getCurrentVersionId());
        result.setVersion(row.getVersion());
        result.setUpdateBy(row.getUpdateBy());
        result.setUpdateTime(row.getUpdateTime());
    }

    private Version toVersion(FileTemplateVersionDO row) {
        Version result = new Version();
        result.setId(row.getId());
        result.setVersionNo(row.getVersionNo());
        result.setSchemaVersion(row.getSchemaVersion());
        result.setRequestId(row.getRequestId());
        result.setReleaseNote(row.getReleaseNote());
        result.setPublishStatus(row.getPublishStatus());
        result.setPublishedBy(row.getPublishedBy());
        result.setPublishedTime(row.getPublishedTime());
        result.setCreateBy(row.getCreateBy());
        result.setCreateTime(row.getCreateTime());
        result.setAssets(assets(row.getId()).stream().map(this::toAsset).toList());
        return result;
    }

    private Asset toAsset(FileTemplateAssetDO row) {
        Asset result = new Asset();
        result.setId(row.getId());
        result.setLocale(row.getLocale());
        result.setOriginalFilename(row.getOriginalFilename());
        result.setContentType(row.getContentType());
        result.setFileSize(row.getFileSize());
        result.setSha256(row.getSha256());
        return result;
    }

    private List<FileTemplateAssetDO> assets(Long versionId) {
        return assetMapper.selectList(Wrappers.<FileTemplateAssetDO>lambdaQuery()
                .eq(FileTemplateAssetDO::getTemplateVersionId, versionId)
                .orderByAsc(FileTemplateAssetDO::getLocale));
    }

    private FileTemplateAssetDO findAsset(Long versionId, String locale) {
        return assetMapper.selectOne(Wrappers.<FileTemplateAssetDO>lambdaQuery()
                .eq(FileTemplateAssetDO::getTemplateVersionId, versionId)
                .eq(FileTemplateAssetDO::getLocale, locale)
                .last("LIMIT 1"));
    }

    private FileTemplateVersionDO findVersion(Long templateId, Integer versionNo) {
        return versionMapper.selectOne(Wrappers.<FileTemplateVersionDO>lambdaQuery()
                .eq(FileTemplateVersionDO::getTemplateId, templateId)
                .eq(FileTemplateVersionDO::getVersionNo, versionNo)
                .last("LIMIT 1"));
    }

    private FileTemplateVersionDO findVersionByRequestId(Long templateId, String requestId) {
        return versionMapper.selectOne(Wrappers.<FileTemplateVersionDO>lambdaQuery()
                .eq(FileTemplateVersionDO::getTemplateId, templateId)
                .eq(FileTemplateVersionDO::getRequestId, requestId)
                .last("LIMIT 1"));
    }

    private FileTemplateDO requireTemplate(Long id) {
        if (id == null) {
            throw invalid("模板ID不能为空");
        }
        FileTemplateDO row = templateMapper.selectOne(Wrappers.<FileTemplateDO>lambdaQuery()
                .eq(FileTemplateDO::getId, id)
                .eq(FileTemplateDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw notFound("模板不存在");
        }
        return row;
    }

    private FileTemplateDO findByCode(String code) {
        return templateMapper.selectOne(Wrappers.<FileTemplateDO>lambdaQuery()
                .eq(FileTemplateDO::getTemplateCode, code)
                .eq(FileTemplateDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    private String resolveCreateCode(String requestedCode, NormalizedDefinition definition, String extension) {
        if (!StringUtils.hasText(requestedCode)) {
            return normalizeTemplateCode("TPL_" + definition.ownerSystem() + "_"
                    + definition.moduleCode() + "_" + globalIdGenerator.nextId());
        }
        Spec builtin = BuiltinFileTemplateCatalog.require(requestedCode);
        if (!Objects.equals(builtin.ownerSystem(), definition.ownerSystem())
                || !Objects.equals(builtin.moduleCode(), definition.moduleCode())
                || !Objects.equals(builtin.nameZh(), definition.nameZh())
                || !Objects.equals(builtin.nameEn(), definition.nameEn())
                || !Objects.equals(builtin.fileExtension(), extension)
                || !Objects.equals(builtin.adminPermissionCode(), definition.adminPermissionCode())
                || !Objects.equals(builtin.merchantPermissionCode(), definition.merchantPermissionCode())) {
            throw invalid("系统内置模板定义与后端目录不一致");
        }
        return builtin.templateCode();
    }

    private List<PermissionOption> permissionOptions(String appCode) {
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, appCode)
                .eq(SysAppDO::getStatus, ENABLED)
                .eq(SysAppDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            return List.of();
        }
        return permissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, app.getId())
                        .eq(SysPermissionDO::getStatus, ENABLED)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .orderByAsc(SysPermissionDO::getPermissionCode))
                .stream()
                .filter(item -> StringUtils.hasText(item.getPermissionCode()))
                .map(item -> {
                    PermissionOption option = new PermissionOption();
                    option.setCode(item.getPermissionCode().trim());
                    option.setName(StringUtils.hasText(item.getPermissionName())
                            ? item.getPermissionName().trim() : item.getPermissionCode().trim());
                    return option;
                })
                .toList();
    }

    private NormalizedDefinition normalizeDefinition(String ownerSystem, String moduleCode, String nameZh,
                                                     String nameEn, String adminPermissionCode,
                                                     String merchantPermissionCode, String remark) {
        String normalizedOwner = normalizeOwnerSystem(ownerSystem);
        String normalizedModule = requireText(moduleCode, "所属模块不能为空").toUpperCase(Locale.ROOT);
        if (!normalizedModule.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw invalid("所属模块编码格式不正确");
        }
        FileTemplateModuleCatalog.require(normalizedOwner, normalizedModule);
        String normalizedNameZh = normalizeBaseName(nameZh, "中文下载文件名");
        String normalizedNameEn = normalizeBaseName(nameEn, "英文下载文件名");
        String normalizedAdminPermission = normalizePermission(adminPermissionCode, "管理系统下载权限编码");
        String normalizedMerchantPermission = normalizePermission(merchantPermissionCode, "商户系统下载权限编码");
        validatePermissionScope(normalizedOwner, normalizedAdminPermission, normalizedMerchantPermission);
        validatePermissionExists("ADMIN", normalizedAdminPermission);
        validatePermissionExists("MERCHANT", normalizedMerchantPermission);
        return new NormalizedDefinition(normalizedOwner, normalizedModule, normalizedNameZh, normalizedNameEn,
                normalizedAdminPermission, normalizedMerchantPermission, trimToNull(remark));
    }

    private void validatePermissionExists(String appCode, String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return;
        }
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, appCode)
                .eq(SysAppDO::getStatus, ENABLED)
                .eq(SysAppDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null || permissionMapper.selectCount(Wrappers.<SysPermissionDO>lambdaQuery()
                .eq(SysPermissionDO::getAppId, app.getId())
                .eq(SysPermissionDO::getPermissionCode, permissionCode)
                .eq(SysPermissionDO::getStatus, ENABLED)
                .eq(SysPermissionDO::getDeleted, NOT_DELETED)) == 0) {
            throw invalid(("ADMIN".equals(appCode) ? "管理系统" : "商户系统") + "下载权限不存在或已停用");
        }
    }

    private void validatePermissionScope(String ownerSystem, String adminPermission, String merchantPermission) {
        if ("ADMIN".equals(ownerSystem)) {
            if (adminPermission == null) {
                throw invalid("管理系统专属模板必须配置管理系统下载权限");
            }
            if (merchantPermission != null) {
                throw invalid("管理系统专属模板不能配置商户系统下载权限");
            }
            return;
        }
        if ("MERCHANT".equals(ownerSystem)) {
            if (merchantPermission == null) {
                throw invalid("商户系统专属模板必须配置商户系统下载权限");
            }
            if (adminPermission != null) {
                throw invalid("商户系统专属模板不能配置管理系统下载权限");
            }
            return;
        }
        if (adminPermission == null || merchantPermission == null) {
            throw invalid("两端共用模板必须分别配置管理系统和商户系统下载权限");
        }
    }

    private String normalizePermission(String permissionCode, String field) {
        String normalized = trimToNull(permissionCode);
        // 系统现有权限编码采用 lowerCamelCase 分段（例如 risk:blacklist:cardBin:template），
        // 这里必须与统一权限模型保持一致，不能仅允许全小写而误拒合法业务权限。
        if (normalized != null && !normalized.matches("[a-z][A-Za-z0-9:-]{2,127}")) {
            throw invalid(field + "格式不正确");
        }
        return normalized;
    }

    private String normalizeBaseName(String value, String field) {
        String name = requireText(value, field + "不能为空");
        if (name.length() > 128 || name.matches(".*[\\\\/\\p{Cntrl}].*")
                || name.matches("(?i).+\\.(xlsx|csv|pdf|docx|zip)$")) {
            throw invalid(field + "只能填写不含扩展名的安全文件名");
        }
        return name;
    }

    private String normalizeOwnerSystem(String ownerSystem) {
        String value = requireText(ownerSystem, "隶属系统不能为空").toUpperCase(Locale.ROOT);
        if (!OWNER_SYSTEMS.contains(value)) {
            throw invalid("隶属系统只允许ADMIN、MERCHANT或SHARED");
        }
        return value;
    }

    private String normalizeTemplateCode(String code) {
        String value = requireText(code, "模板编码不能为空").toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw invalid("模板编码格式不正确");
        }
        return value;
    }

    private String normalizeRequestId(String requestId) {
        String value = requireText(requestId, "requestId不能为空");
        if (!value.matches("[A-Za-z0-9_-]{8,64}")) {
            throw invalid("requestId格式不正确");
        }
        return value;
    }

    private String normalizeLocale(String locale) {
        return StringUtils.hasText(locale) && locale.trim().toLowerCase(Locale.ROOT).startsWith("en")
                ? "en-US" : "zh-CN";
    }

    private boolean sameDefinition(FileTemplateDO row, NormalizedDefinition value, String extension) {
        return Objects.equals(row.getOwnerSystem(), value.ownerSystem())
                && Objects.equals(row.getModuleCode(), value.moduleCode())
                && Objects.equals(row.getNameZh(), value.nameZh())
                && Objects.equals(row.getNameEn(), value.nameEn())
                && Objects.equals(row.getFileExtension(), extension)
                && Objects.equals(row.getAdminPermissionCode(), value.adminPermissionCode())
                && Objects.equals(row.getMerchantPermissionCode(), value.merchantPermissionCode())
                && Objects.equals(row.getRemark(), value.remark());
    }

    private String objectKey(FileTemplateDO template, Integer versionNo, String locale) {
        return "templates/" + template.getOwnerSystem().toLowerCase(Locale.ROOT) + "/"
                + template.getModuleCode().toLowerCase(Locale.ROOT) + "/" + template.getTemplateCode() + "/"
                + versionNo + "/" + locale + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + template.getFileExtension();
    }

    private TemplateObjectStorageService requireStorage() {
        TemplateObjectStorageService storage = storageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "系统模板对象存储未启用，请检查Nacos对象存储配置");
        }
        return storage;
    }

    private void registerRollbackCleanup(TemplateObjectStorageService storage, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        storage.delete(objectKey);
                    } catch (RuntimeException exception) {
                        log.error("template object rollback cleanup failed, keyHash={}",
                                Integer.toHexString(objectKey.hashCode()), exception);
                    }
                }
            }
        });
    }

    private String normalizedProvider() {
        return StringUtils.hasText(storageProperties.getProvider())
                ? storageProperties.getProvider().trim().toUpperCase(Locale.ROOT) : "S3";
    }

    private String operatorName() {
        InternalAuthAccount account = requireAccount();
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName().trim();
        }
        return StringUtils.hasText(account.getLoginAccount())
                ? account.getLoginAccount().trim() : String.valueOf(account.getAccountId());
    }

    private InternalAuthAccount requireAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录账号上下文缺失");
        }
        return account;
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

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw invalid(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private ServiceException notFound(String message) {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), message);
    }

    private record NormalizedDefinition(String ownerSystem, String moduleCode, String nameZh, String nameEn,
                                        String adminPermissionCode, String merchantPermissionCode, String remark) {
    }

    /** 摘要计算独立封装，避免下载路径信任对象存储返回内容。 */
    static final class TemplateDigest {
        private TemplateDigest() {
        }

        static String sha256(byte[] content) {
            try {
                return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }
}
