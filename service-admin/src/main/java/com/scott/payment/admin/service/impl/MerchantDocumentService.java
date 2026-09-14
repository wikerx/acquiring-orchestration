package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.BizDocumentDO;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageService;
import com.scott.payment.admin.mapper.BizDocumentMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDocumentService
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户合规资料领域服务，负责文件安全校验、私有对象存储和数据库元数据一致性
 * @status : create
 *
 * <p>文件正文只进入私有对象存储，数据库仅保存归属、摘要和存储定位信息。下载接口不暴露
 * Bucket 或 Object Key，上传回滚和删除提交后的对象清理由事务同步器协调。</p>
 */
@Slf4j
@Service
public class MerchantDocumentService {

    /** 未删除标识；固定为 0，非敏感。 */
    private static final int NOT_DELETED = 0;

    /** 商户 KYB 资料业务类型；固定协议值，不允许为空，非敏感。 */
    private static final String DOCUMENT_BIZ_TYPE = "MERCHANT_KYB";

    /** 文件已完成对象存储写入的状态；固定协议值，不允许为空，非敏感。 */
    private static final String DOCUMENT_STATUS_UPLOADED = "UPLOADED";

    /** 默认单文件大小上限，单位字节，当前为 20 MiB。 */
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    /** 商户主档 Mapper；用于校验资料归属商户是否存在，不允许为空。 */
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /** 资料元数据 Mapper；只持久化归属、摘要和对象定位，不保存正文。 */
    private final BizDocumentMapper documentMapper;

    /** 对象存储实现提供器；存储未启用时允许没有 Bean，并在调用时返回明确错误。 */
    private final ObjectProvider<ObjectStorageService> objectStorageProvider;

    /** 对象存储配置；包含 Bucket 和访问凭证，凭证禁止写入日志。 */
    private final ObjectStorageProperties storageProperties;

    /**
     * 创建商户资料服务。
     *
     * @param merchantInfoMapper 商户归属校验 Mapper
     * @param documentMapper 资料元数据 Mapper
     * @param objectStorageProvider 可选对象存储实现
     * @param storageProperties 对象存储配置
     */
    public MerchantDocumentService(BaseMerchantInfoMapper merchantInfoMapper,
                                   BizDocumentMapper documentMapper,
                                   ObjectProvider<ObjectStorageService> objectStorageProvider,
                                   ObjectStorageProperties storageProperties) {
        this.merchantInfoMapper = merchantInfoMapper;
        this.documentMapper = documentMapper;
        this.objectStorageProvider = objectStorageProvider;
        this.storageProperties = storageProperties;
    }

    /**
     * 上传商户合规资料并保存可审计元数据。
     *
     * @param merchantId 平台商户号
     * @param documentType 资料类型
     * @param file PDF、JPEG 或 PNG 文件
     * @return 已保存资料元数据
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantOnboardingDTOs.Document upload(String merchantId,
                                                  String documentType,
                                                  MultipartFile file) {
        String normalizedMerchantId = requireMerchant(merchantId).getMerchantId();
        String normalizedDocumentType = normalizeDocumentType(documentType);
        byte[] content = readAndValidate(file);
        DetectedFileType fileType = detectFileType(content);
        String originalFilename = safeFilename(file == null ? null : file.getOriginalFilename(), fileType.extension());
        String objectKey = objectKey(normalizedMerchantId, fileType.extension());
        String sha256 = sha256(content);
        ObjectStorageService storage = requireStorage();

        storage.put(objectKey, fileType.contentType(), content);
        registerRollbackCleanup(storage, objectKey);
        try {
            LocalDateTime now = LocalDateTime.now();
            BizDocumentDO row = new BizDocumentDO();
            row.setBizType(DOCUMENT_BIZ_TYPE);
            row.setBizId(normalizedMerchantId);
            row.setDocumentType(normalizedDocumentType);
            row.setOriginalFilename(originalFilename);
            row.setContentType(fileType.contentType());
            row.setFileSize((long) content.length);
            row.setSha256(sha256);
            row.setStorageProvider(normalizedProvider());
            row.setBucketName(storageProperties.getBucket());
            row.setObjectKey(objectKey);
            row.setDocumentStatus(DOCUMENT_STATUS_UPLOADED);
            row.setUploadedBy(operatorName());
            row.setGmtCreate(now);
            row.setGmtModified(now);
            row.setDeleted(NOT_DELETED);
            documentMapper.insert(row);
            return toDTO(row);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                deleteQuietly(storage, objectKey);
            }
            throw exception;
        }
    }

    /**
     * 下载商户资料并校验对象内容摘要。
     *
     * @param merchantId 平台商户号
     * @param documentId 资料主键
     * @return 文件名、内容类型和正文
     */
    @DS(DataSourceName.MASTER)
    public DocumentDownload download(String merchantId, Long documentId) {
        requireMerchant(merchantId);
        BizDocumentDO document = requireDocument(merchantId, documentId);
        byte[] content = requireStorage().get(document.getObjectKey());
        if (!MessageDigest.isEqual(
                document.getSha256().getBytes(StandardCharsets.US_ASCII),
                sha256(content).getBytes(StandardCharsets.US_ASCII))) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "商户资料完整性校验失败");
        }
        return new DocumentDownload(document.getOriginalFilename(), document.getContentType(), content);
    }

    /**
     * 软删除资料元数据，并在数据库事务提交后删除对象正文。
     *
     * @param merchantId 平台商户号
     * @param documentId 资料主键
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void delete(String merchantId, Long documentId) {
        requireMerchant(merchantId);
        BizDocumentDO document = requireDocument(merchantId, documentId);
        int affected = documentMapper.update(null, Wrappers.<BizDocumentDO>lambdaUpdate()
                .set(BizDocumentDO::getDeleted, 1)
                .set(BizDocumentDO::getGmtModified, LocalDateTime.now())
                .eq(BizDocumentDO::getId, document.getId())
                .eq(BizDocumentDO::getBizType, DOCUMENT_BIZ_TYPE)
                .eq(BizDocumentDO::getBizId, document.getBizId())
                .eq(BizDocumentDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户资料状态已变化，请刷新后重试");
        }
        deleteAfterCommit(requireStorage(), document.getObjectKey());
    }

    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号不能为空");
        }
        BaseMerchantInfoDO merchant = merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId.trim())
                .eq(BaseMerchantInfoDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return merchant;
    }

    private BizDocumentDO requireDocument(String merchantId, Long documentId) {
        if (documentId == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "资料ID不能为空");
        }
        BizDocumentDO document = documentMapper.selectOne(Wrappers.<BizDocumentDO>lambdaQuery()
                .eq(BizDocumentDO::getId, documentId)
                .eq(BizDocumentDO::getBizType, DOCUMENT_BIZ_TYPE)
                .eq(BizDocumentDO::getBizId, merchantId.trim())
                .eq(BizDocumentDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (document == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户资料不存在");
        }
        return document;
    }

    /**
     * 获取当前启用的对象存储实现，配置或 Bean 缺失时禁止继续处理文件正文。
     *
     * @return 可用的对象存储服务
     * @throws ServiceException 对象存储未启用或实现未加载时抛出
     */
    private ObjectStorageService requireStorage() {
        ObjectStorageService storage = objectStorageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "对象存储未启用，请检查 Nacos 和运行环境凭证配置");
        }
        return storage;
    }

    /**
     * 在进入对象存储前读取并校验文件大小，避免空文件和超限文件产生存储副作用。
     *
     * @param file 管理端上传文件，可为空
     * @return 已通过大小校验的文件正文
     * @throws ServiceException 文件为空、超限或读取失败时抛出
     */
    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "上传文件不能为空");
        }
        long maxSize = storageProperties.getMaxFileSizeBytes() > 0
                ? storageProperties.getMaxFileSizeBytes() : DEFAULT_MAX_FILE_SIZE_BYTES;
        if (file.getSize() > maxSize) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "单个商户资料不能超过20 MiB");
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > maxSize) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "上传文件大小不合法");
            }
            return content;
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "读取上传文件失败");
        }
    }

    /**
     * 根据文件魔数识别受支持类型，不信任请求 MIME 或文件扩展名。
     *
     * @param content 文件二进制正文，不允许为空
     * @return 受控 MIME 类型和扩展名
     * @throws ServiceException 文件魔数不属于 PDF、JPEG 或 PNG 时抛出
     */
    private DetectedFileType detectFileType(byte[] content) {
        if (startsWith(content, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D})) {
            return new DetectedFileType("application/pdf", "pdf");
        }
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return new DetectedFileType("image/jpeg", "jpg");
        }
        if (startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new DetectedFileType("image/png", "png");
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "仅支持 PDF、JPEG 或 PNG 资料");
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalizeDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "资料类型不能为空");
        }
        String normalized = documentType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,64}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "资料类型格式不正确");
        }
        return normalized;
    }

    /**
     * 清理客户端文件名中的路径和控制字符，避免下载响应出现路径穿越或响应头污染。
     *
     * @param originalFilename 客户端原始文件名，可为空且不可信
     * @param extension 由文件魔数确定的安全扩展名
     * @return 长度不超过 255 的安全文件名
     */
    private String safeFilename(String originalFilename, String extension) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "document." + extension;
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (!StringUtils.hasText(filename)) {
            filename = "document." + extension;
        }
        return filename.length() <= 255 ? filename : filename.substring(0, 255);
    }

    /**
     * 生成不可预测的商户私有对象键，按年月分层且不使用客户端文件名。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param extension 由文件魔数确定的安全扩展名
     * @return 仅供服务端持久化和访问的私有对象键
     */
    private String objectKey(String merchantId, String extension) {
        LocalDate today = LocalDate.now();
        return "merchants/" + merchantId + "/" + today.getYear() + "/"
                + String.format(Locale.ROOT, "%02d", today.getMonthValue()) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    /**
     * 计算文件正文 SHA-256 摘要，用于数据库元数据与下载对象的一致性校验。
     *
     * @param content 文件二进制正文，不允许为空
     * @return 小写十六进制 SHA-256 摘要
     */
    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizedProvider() {
        return StringUtils.hasText(storageProperties.getProvider())
                ? storageProperties.getProvider().trim().toUpperCase(Locale.ROOT) : "S3";
    }

    private String operatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName().trim();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "system";
    }

    private MerchantOnboardingDTOs.Document toDTO(BizDocumentDO row) {
        MerchantOnboardingDTOs.Document dto = new MerchantOnboardingDTOs.Document();
        dto.setId(row.getId());
        dto.setDocumentType(row.getDocumentType());
        dto.setOriginalFilename(row.getOriginalFilename());
        dto.setContentType(row.getContentType());
        dto.setFileSize(row.getFileSize());
        dto.setSha256(row.getSha256());
        dto.setDocumentStatus(row.getDocumentStatus());
        dto.setGmtCreate(row.getGmtCreate());
        return dto;
    }

    /**
     * 注册上传回滚补偿；数据库事务未提交时删除已经写入的对象正文。
     *
     * <p>对象存储不参与数据库事务，因此必须在事务完成回调中补偿。无活动事务时由调用方
     * 的异常分支立即清理，避免重复注册。</p>
     *
     * @param storage 对象存储服务，不允许为空
     * @param objectKey 待补偿的私有对象键，不允许为空
     */
    private void registerRollbackCleanup(ObjectStorageService storage, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(storage, objectKey);
                }
            }
        });
    }

    /**
     * 在资料元数据软删除事务提交后删除对象正文，避免数据库回滚但文件已丢失。
     *
     * @param storage 对象存储服务，不允许为空
     * @param objectKey 待删除的私有对象键，不允许为空
     */
    private void deleteAfterCommit(ObjectStorageService storage, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.delete(objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(storage, objectKey);
            }
        });
    }

    /**
     * 执行尽力而为的对象清理，补偿失败只记录对象键和异常类型，不记录文件正文或凭证。
     *
     * <p>该方法用于事务完成回调，不能继续向已完成的数据库事务传播异常。</p>
     *
     * @param storage 对象存储服务，不允许为空
     * @param objectKey 待清理的私有对象键，不允许为空
     */
    private void deleteQuietly(ObjectStorageService storage, String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("merchant document object cleanup failed, objectKey: {}, exceptionType: {}",
                    objectKey, exception.getClass().getSimpleName());
        }
    }

    /**
     * 商户资料下载内容，仅在 Controller 组装受控二进制响应。
     *
     * @param filename 安全清洗后的下载文件名
     * @param contentType 已通过魔数校验的 MIME 类型
     * @param content 文件二进制正文，敏感且禁止记录日志
     */
    public record DocumentDownload(String filename, String contentType, byte[] content) {
    }

    private record DetectedFileType(String contentType, String extension) {
    }
}
