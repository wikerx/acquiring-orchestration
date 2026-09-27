package com.scott.payment.merchant.application.profile;

import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.service.profile.MerchantProfileDocumentService;
import com.scott.payment.merchant.service.profile.MerchantProfileDocumentService.DocumentDownload;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileDocumentApplicationService
 * @date : 2026-09-21 09:45
 * @email : scott_x@163.com
 * @description : 商户资料变更文件应用服务，编排上传、下载和删除用例
 * @status : create
 */
@Service
public class MerchantProfileDocumentApplicationService {

    private final MerchantProfileDocumentService documentService;

    /** 创建商户资料变更文件应用服务。 */
    public MerchantProfileDocumentApplicationService(MerchantProfileDocumentService documentService) {
        this.documentService = documentService;
    }

    /** 上传资料文件。 */
    public MerchantProfileChangeDTOs.Document upload(
            String merchantId, String requestNo, String documentType, MultipartFile file) {
        return documentService.upload(merchantId, requestNo, documentType, file);
    }

    /** 下载资料文件。 */
    public DocumentDownload download(String merchantId, Long documentId) {
        return documentService.download(merchantId, documentId);
    }

    /** 删除活动申请中的待审资料文件。 */
    public void delete(String merchantId, String requestNo, Long documentId) {
        documentService.delete(merchantId, requestNo, documentId);
    }
}
