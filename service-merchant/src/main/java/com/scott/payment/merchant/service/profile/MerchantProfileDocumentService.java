package com.scott.payment.merchant.service.profile;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.merchant.entity.MerchantDocumentDO;
import com.scott.payment.component.db.merchant.entity.MerchantProfileChangeRequestDO;
import com.scott.payment.component.db.merchant.mapper.MerchantDocumentMapper;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.merchant.infrastructure.storage.ObjectStorageService;
import com.scott.payment.merchant.service.impl.MerchantProfileChangeRequestService;
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
 * @classname : MerchantProfileDocumentService
 * @date : 2026-09-21 09:45
 * @email : scott_x@163.com
 * @description : 商户资料变更文件服务，确保对象存储正文、申请归属和元数据状态一致
 * @status : create
 */
@Slf4j
@Service
public class MerchantProfileDocumentService {

    private static final int NOT_DELETED = 0;
    private static final String DOCUMENT_BIZ_TYPE = "MERCHANT_KYB";
    private static final String STATUS_PENDING = "PENDING_REVIEW";
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    private final MerchantDocumentMapper documentMapper;
    private final MerchantProfileChangeRequestService changeRequestService;
    private final ObjectProvider<ObjectStorageService> objectStorageProvider;
    private final ObjectStorageProperties storageProperties;

    /** 创建商户资料变更文件服务。 */
    public MerchantProfileDocumentService(
            MerchantDocumentMapper documentMapper,
            MerchantProfileChangeRequestService changeRequestService,
            ObjectProvider<ObjectStorageService> objectStorageProvider,
            ObjectStorageProperties storageProperties) {
        this.documentMapper = documentMapper;
        this.changeRequestService = changeRequestService;
        this.objectStorageProvider = objectStorageProvider;
        this.storageProperties = storageProperties;
    }

    /** 上传并绑定活动草稿或补件申请，文件在审核通过前不进入正式资料集合。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileChangeDTOs.Document upload(
            String merchantId, String requestNo, String documentType, MultipartFile file) {
        requireEditableRequest(merchantId, requestNo);
        String normalizedType = normalizeDocumentType(documentType);
        byte[] content = readAndValidate(file);
        DetectedFileType fileType = detectFileType(content);
        String objectKey = objectKey(merchantId, requestNo, fileType.extension());
        ObjectStorageService storage = requireStorage();
        storage.put(objectKey, fileType.contentType(), content);
        registerRollbackCleanup(storage, objectKey);
        try {
            LocalDateTime now = LocalDateTime.now();
            MerchantDocumentDO row = new MerchantDocumentDO();
            row.setBizType(DOCUMENT_BIZ_TYPE);
            row.setBizId(merchantId);
            row.setRequestNo(requestNo);
            row.setDocumentType(normalizedType);
            row.setOriginalFilename(safeFilename(
                    file == null ? null : file.getOriginalFilename(), fileType.extension()));
            row.setContentType(fileType.contentType());
            row.setFileSize((long) content.length);
            row.setSha256(sha256(content));
            row.setStorageProvider(normalizedProvider());
            row.setBucketName(storageProperties.getBucket());
            row.setObjectKey(objectKey);
            row.setDocumentStatus(STATUS_PENDING);
            row.setUploadedBy(operatorName());
            row.setGmtCreate(now);
            row.setGmtModified(now);
            row.setDeleted(NOT_DELETED);
            documentMapper.insert(row);
            return toResponse(row);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                deleteQuietly(storage, objectKey);
            }
            throw exception;
        }
    }

    /** 下载当前商户拥有的资料并校验 SHA-256 摘要。 */
    @DS(DataSourceName.MASTER)
    public DocumentDownload download(String merchantId, Long documentId) {
        MerchantDocumentDO document = requireDocument(merchantId, documentId);
        byte[] content = requireStorage().get(document.getObjectKey());
        if (!MessageDigest.isEqual(
                document.getSha256().getBytes(StandardCharsets.US_ASCII),
                sha256(content).getBytes(StandardCharsets.US_ASCII))) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "商户资料完整性校验失败");
        }
        return new DocumentDownload(document.getOriginalFilename(), document.getContentType(), content);
    }

    /** 删除活动草稿或补件申请中的待审资料，正式资料不允许由该接口删除。 */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void delete(String merchantId, String requestNo, Long documentId) {
        requireEditableRequest(merchantId, requestNo);
        MerchantDocumentDO document = requireDocument(merchantId, documentId);
        if (!requestNo.equals(document.getRequestNo()) || !STATUS_PENDING.equals(document.getDocumentStatus())) {
            throw invalid("only pending documents of the active request can be deleted");
        }
        int affected = documentMapper.update(null, Wrappers.<MerchantDocumentDO>lambdaUpdate()
                .set(MerchantDocumentDO::getDeleted, 1)
                .set(MerchantDocumentDO::getGmtModified, LocalDateTime.now())
                .eq(MerchantDocumentDO::getId, document.getId())
                .eq(MerchantDocumentDO::getBizId, merchantId)
                .eq(MerchantDocumentDO::getRequestNo, requestNo)
                .eq(MerchantDocumentDO::getDeleted, NOT_DELETED));
        if (affected != 1) {
            throw invalid("merchant document status has changed, please refresh");
        }
        deleteAfterCommit(requireStorage(), document.getObjectKey());
    }

    private void requireEditableRequest(String merchantId, String requestNo) {
        MerchantProfileChangeRequestDO active = changeRequestService.findActive(merchantId);
        if (active == null || !StringUtils.hasText(requestNo) || !requestNo.equals(active.getRequestNo())) {
            throw invalid("active merchant profile change request was not found");
        }
        if (!MerchantProfileChangeRequestService.STATUS_DRAFT.equals(active.getStatus())
                && !MerchantProfileChangeRequestService.STATUS_SUPPLEMENT_REQUIRED.equals(active.getStatus())) {
            throw invalid("documents cannot be changed in current request status");
        }
    }

    private MerchantDocumentDO requireDocument(String merchantId, Long documentId) {
        if (!StringUtils.hasText(merchantId) || documentId == null) {
            throw invalid("merchant id and document id are required");
        }
        MerchantDocumentDO row = documentMapper.selectOne(Wrappers.<MerchantDocumentDO>lambdaQuery()
                .eq(MerchantDocumentDO::getId, documentId)
                .eq(MerchantDocumentDO::getBizType, DOCUMENT_BIZ_TYPE)
                .eq(MerchantDocumentDO::getBizId, merchantId)
                .eq(MerchantDocumentDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant document was not found");
        }
        return row;
    }

    private ObjectStorageService requireStorage() {
        ObjectStorageService storage = objectStorageProvider.getIfAvailable();
        if (storage == null || !storageProperties.isEnabled()) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "对象存储未启用，请检查 Nacos 和运行环境凭证配置");
        }
        return storage;
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("upload file is required");
        long maxSize = storageProperties.getMaxFileSizeBytes() > 0
                ? storageProperties.getMaxFileSizeBytes() : DEFAULT_MAX_FILE_SIZE_BYTES;
        if (file.getSize() > maxSize) throw invalid("merchant document exceeds size limit");
        try {
            byte[] content = file.getBytes();
            if (content.length == 0 || content.length > maxSize) {
                throw invalid("merchant document size is invalid");
            }
            return content;
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "读取上传文件失败");
        }
    }

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
        throw invalid("only PDF, JPEG or PNG documents are supported");
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) return false;
        }
        return true;
    }

    private String normalizeDocumentType(String value) {
        if (!StringUtils.hasText(value)) throw invalid("document type is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_]{2,64}")) throw invalid("document type is invalid");
        return normalized;
    }

    private String safeFilename(String value, String extension) {
        String filename = StringUtils.hasText(value) ? value.trim() : "document." + extension;
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (!StringUtils.hasText(filename)) filename = "document." + extension;
        return filename.length() <= 255 ? filename : filename.substring(0, 255);
    }

    private String objectKey(String merchantId, String requestNo, String extension) {
        LocalDate today = LocalDate.now();
        return "merchants/" + merchantId + "/profile-changes/" + requestNo + "/"
                + today.getYear() + "/" + String.format(Locale.ROOT, "%02d", today.getMonthValue())
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

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
        if (account == null) return "system";
        if (StringUtils.hasText(account.getRealName())) return account.getRealName().trim();
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "system";
    }

    private MerchantProfileChangeDTOs.Document toResponse(MerchantDocumentDO row) {
        MerchantProfileChangeDTOs.Document response = new MerchantProfileChangeDTOs.Document();
        response.setId(row.getId());
        response.setRequestNo(row.getRequestNo());
        response.setDocumentType(row.getDocumentType());
        response.setOriginalFilename(row.getOriginalFilename());
        response.setContentType(row.getContentType());
        response.setFileSize(row.getFileSize());
        response.setSha256(row.getSha256());
        response.setDocumentStatus(row.getDocumentStatus());
        response.setGmtCreate(row.getGmtCreate());
        return response;
    }

    private void registerRollbackCleanup(ObjectStorageService storage, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(storage, objectKey);
            }
        });
    }

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

    private void deleteQuietly(ObjectStorageService storage, String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("merchant profile document cleanup failed, objectKey: {}, exceptionType: {}",
                    objectKey, exception.getClass().getSimpleName());
        }
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /** 商户资料下载内容，仅供控制器组装受控二进制响应。 */
    public record DocumentDownload(String filename, String contentType, byte[] content) {
    }

    private record DetectedFileType(String contentType, String extension) {
    }
}
