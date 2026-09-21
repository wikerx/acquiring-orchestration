package com.scott.payment.merchant.api.profile;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.operation.constant.OperatorTypeConstants;
import com.scott.payment.merchant.application.profile.MerchantProfileDocumentApplicationService;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.service.profile.MerchantProfileDocumentService.DocumentDownload;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileDocumentController
 * @date : 2026-09-21 09:45
 * @email : scott_x@163.com
 * @description : 商户资料变更文件接口，只允许当前认证商户访问自己的私有资料
 * @status : create
 */
@RestController
@RequestMapping("/merchant/info/change-requests/{requestNo}/documents")
public class MerchantProfileDocumentController {

    private final MerchantProfileDocumentApplicationService applicationService;

    /** 创建商户资料变更文件接口。 */
    public MerchantProfileDocumentController(MerchantProfileDocumentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 上传 PDF、JPEG 或 PNG 资料并绑定当前活动申请。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户资料维护",
            businessType = OperationTypeConstants.CREATE,
            operation = "上传商户资料变更文件",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<MerchantProfileChangeDTOs.Document> upload(
            @PathVariable("requestNo") String requestNo,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        return success(applicationService.upload(currentMerchantId(), requestNo, documentType, file));
    }

    /** 下载当前商户拥有的资料，响应禁止缓存且不暴露对象存储定位。 */
    @GetMapping("/{documentId}/download")
    @RequiresPermission("merchant:info:view")
    public ResponseEntity<byte[]> download(
            @PathVariable("requestNo") String requestNo,
            @PathVariable("documentId") Long documentId) {
        DocumentDownload file = applicationService.download(currentMerchantId(), documentId);
        String encodedFilename = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .body(file.content());
    }

    /** 删除活动草稿或补件申请中的待审资料。 */
    @DeleteMapping("/{documentId}")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户资料维护",
            businessType = OperationTypeConstants.DELETE,
            operation = "删除商户资料变更文件",
            operatorType = OperatorTypeConstants.MERCHANT_USER,
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<Void> delete(
            @PathVariable("requestNo") String requestNo,
            @PathVariable("documentId") Long documentId) {
        applicationService.delete(currentMerchantId(), requestNo, documentId);
        return success();
    }

    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId().trim();
    }
}
