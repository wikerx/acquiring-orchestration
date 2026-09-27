package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.service.impl.MerchantDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantDocumentApplicationService
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 管理端商户合规资料应用服务，隔离 Web 层与对象存储领域服务并保持控制器轻量
 * @status : create
 */
@Service
public class AdminMerchantDocumentApplicationService {

    /** 商户资料领域服务；负责归属校验、文件安全校验和存储一致性，不允许为空。 */
    private final MerchantDocumentService merchantDocumentService;

    /**
     * 创建商户资料应用服务。
     *
     * @param merchantDocumentService 商户资料领域服务，不允许为空
     */
    public AdminMerchantDocumentApplicationService(MerchantDocumentService merchantDocumentService) {
        this.merchantDocumentService = merchantDocumentService;
    }

    /**
     * 上传商户合规资料。
     *
     * @param merchantId 平台商户号
     * @param documentType 资料类型编码
     * @param file PDF、JPEG 或 PNG 文件
     * @return 已保存的资料元数据
     */
    public MerchantOnboardingDTOs.Document upload(String merchantId, String documentType, MultipartFile file) {
        return merchantDocumentService.upload(merchantId, documentType, file);
    }

    /**
     * 下载商户合规资料并返回受控文件内容。
     *
     * @param merchantId 平台商户号
     * @param documentId 资料主键
     * @return 文件名、内容类型和二进制正文
     */
    public MerchantDocumentService.DocumentDownload download(String merchantId, Long documentId) {
        return merchantDocumentService.download(merchantId, documentId);
    }

    /**
     * 删除商户合规资料，数据库提交后清理对象正文。
     *
     * @param merchantId 平台商户号
     * @param documentId 资料主键
     */
    public void delete(String merchantId, Long documentId) {
        merchantDocumentService.delete(merchantId, documentId);
    }
}
