package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantDocumentApplicationService;
import com.scott.payment.admin.dto.merchant.MerchantOnboardingDTOs;
import com.scott.payment.admin.service.impl.MerchantDocumentService.DocumentDownload;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * @classname : AdminMerchantDocumentController
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 管理后台商户合规资料接口，负责权限校验、Multipart 参数接收和受控下载响应组装
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchants/{merchantId}/documents")
public class AdminMerchantDocumentController {

    /** 商户资料上传、下载与删除用例编排服务；不允许为空，非敏感 Bean 引用。 */
    private final AdminMerchantDocumentApplicationService applicationService;

    /**
     * 创建管理后台商户资料控制器。
     *
     * @param applicationService 商户资料应用服务，不允许为空
     */
    public AdminMerchantDocumentController(AdminMerchantDocumentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 上传 PDF、JPEG 或 PNG 商户资料，文件正文由对象存储服务持久化。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param documentType 资料类型编码，不允许为空
     * @param file 待上传文件，最大尺寸由对象存储配置限制
     * @return 不包含底层 Bucket 和 Object Key 的资料元数据
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("merchant:info:edit")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.CREATE, operation = "上传商户资料")
    public CommonResult<MerchantOnboardingDTOs.Document> upload(
            @PathVariable("merchantId") String merchantId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        return success(applicationService.upload(merchantId, documentType, file));
    }

    /**
     * 下载商户资料，响应禁止浏览器和中间代理缓存，且不暴露底层对象存储地址。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param documentId 商户资料主键，不允许为空
     * @return 带安全响应头的文件二进制响应
     */
    @GetMapping("/{documentId}/download")
    @RequiresPermission("merchant:info:detail")
    public ResponseEntity<byte[]> download(@PathVariable("merchantId") String merchantId,
                                           @PathVariable("documentId") Long documentId) {
        DocumentDownload file = applicationService.download(merchantId, documentId);
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

    /**
     * 软删除资料元数据，并在数据库事务提交后清理对象存储正文。
     *
     * @param merchantId 平台商户号，不允许为空
     * @param documentId 商户资料主键，不允许为空
     * @return 无数据成功响应
     */
    @DeleteMapping("/{documentId}")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.DELETE, operation = "删除商户资料")
    public CommonResult<Void> delete(@PathVariable("merchantId") String merchantId,
                                     @PathVariable("documentId") Long documentId) {
        applicationService.delete(merchantId, documentId);
        return success();
    }
}
