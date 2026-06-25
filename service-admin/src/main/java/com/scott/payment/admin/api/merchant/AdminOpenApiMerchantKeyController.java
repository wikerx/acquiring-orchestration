package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantInfoApplicationService;
import com.scott.payment.admin.application.system.AdminOperLogApplicationService;
import com.scott.payment.admin.dto.SysOperLogDTO;
import com.scott.payment.admin.dto.SysOperLogQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.security.openapi.OpenApiKeyCopyResponse;
import com.scott.payment.component.security.openapi.OpenApiKeyDownloadFile;
import com.scott.payment.component.security.openapi.OpenApiKeyExportFormat;
import com.scott.payment.component.security.openapi.OpenApiKeyExportRequest;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiKeyAuditService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台 OpenAPI 商户密钥材料接口，负责运营侧查询、复制、下载和轮换商户接入材料。
 */
@RestController
@RequestMapping("/admin/openapi/merchant-keys")
public class AdminOpenApiMerchantKeyController {

    private static final String OPENAPI_KEY_MODULE_NAME = "OpenAPI对接材料";

    private final OpenApiMerchantKeyMaterialService keyMaterialService;
    private final OpenApiKeyAuditService keyAuditService;
    private final AdminMerchantInfoApplicationService merchantInfoApplicationService;
    private final AdminOperLogApplicationService adminOperLogApplicationService;

    /**
     * 创建管理后台 OpenAPI 商户密钥材料接口。
     *
     * @param keyMaterialService            密钥材料统一服务
     * @param keyAuditService               密钥审计辅助服务
     * @param merchantInfoApplicationService 商户资料应用服务
     * @param adminOperLogApplicationService 操作日志应用服务
     */
    public AdminOpenApiMerchantKeyController(OpenApiMerchantKeyMaterialService keyMaterialService,
                                             OpenApiKeyAuditService keyAuditService,
                                             AdminMerchantInfoApplicationService merchantInfoApplicationService,
                                             AdminOperLogApplicationService adminOperLogApplicationService) {
        this.keyMaterialService = keyMaterialService;
        this.keyAuditService = keyAuditService;
        this.merchantInfoApplicationService = merchantInfoApplicationService;
        this.adminOperLogApplicationService = adminOperLogApplicationService;
    }

    /**
     * 查询商户 OpenAPI 对接材料概要。
     *
     * @param merchantId 商户号
     * @return 对接材料概要
     */
    @GetMapping("/{merchantId}")
    @RequiresPermission("merchant:material:view")
    public CommonResult<OpenApiMerchantKeyMaterialVO> getMaterial(@PathVariable("merchantId") String merchantId) {
        return success(keyMaterialService.queryMaterial(merchantId));
    }

    /**
     * 复制商户 OpenAPI 接入材料。响应内容可能包含敏感密钥，切勿写入日志。
     *
     * @param merchantId 商户号
     * @param request    复制请求
     * @return 可复制文本
     */
    @PostMapping("/{merchantId}/copy")
    @RequiresPermission("merchant:material:copy")
    @OperationLog(moduleName = "OpenAPI对接材料", businessType = OperationTypeConstants.EXPORT, operation = "复制商户OpenAPI对接材料")
    public CommonResult<OpenApiKeyCopyResponse> copy(@PathVariable("merchantId") String merchantId,
                                                     @RequestBody OpenApiKeyExportRequest request) {
        requirePrivateMaterialPermission(request == null ? null : request.getKeyType());
        return success(keyMaterialService.copy(merchantId, request));
    }

    /**
     * 查看商户 OpenAPI 接入材料原文。敏感材料只返回给具备对应权限的后台账号。
     *
     * @param merchantId 商户号
     * @param request    查看请求
     * @return 可查看文本
     */
    @PostMapping("/{merchantId}/view")
    @RequiresPermission("merchant:material:copy")
    @OperationLog(moduleName = "OpenAPI对接材料", businessType = OperationTypeConstants.QUERY, operation = "查看商户OpenAPI对接材料")
    public CommonResult<OpenApiKeyCopyResponse> view(@PathVariable("merchantId") String merchantId,
                                                     @RequestBody OpenApiKeyExportRequest request) {
        requirePrivateMaterialPermission(request == null ? null : request.getKeyType());
        return success(keyMaterialService.copy(merchantId, request));
    }

    /**
     * 下载商户 OpenAPI 接入材料文件。
     *
     * @param merchantId 商户号
     * @param keyType    材料类型
     * @param format     下载格式
     * @return 附件文件
     */
    @GetMapping("/{merchantId}/download")
    @RequiresPermission("merchant:material:download")
    @OperationLog(moduleName = "OpenAPI对接材料", businessType = OperationTypeConstants.EXPORT, operation = "下载商户OpenAPI对接材料")
    public ResponseEntity<byte[]> download(@PathVariable("merchantId") String merchantId,
                                           @RequestParam("keyType") OpenApiKeyType keyType,
                                           @RequestParam(value = "format", required = false) OpenApiKeyExportFormat format) {
        requirePrivateMaterialPermission(keyType);
        return toDownloadResponse(keyMaterialService.download(merchantId, keyType, format));
    }

    /**
     * 轮换商户 OpenAPI 密钥。沿用既有商户密钥领域服务，避免轮换规则分叉。
     *
     * @param merchantId 商户号
     * @param request    轮换请求
     * @return 最新一次性安全材料
     */
    @PostMapping("/{merchantId}/rotate")
    @RequiresPermission("merchant:key:rotate")
    @OperationLog(moduleName = "OpenAPI对接材料", businessType = OperationTypeConstants.UPDATE, operation = "轮换商户OpenAPI密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotate(@PathVariable("merchantId") String merchantId,
                                                                 @RequestBody OpenApiKeyExportRequest request) {
        OpenApiKeyType keyType = request == null ? null : request.getKeyType();
        if (keyType == OpenApiKeyType.JWT_KEY) {
            return success(merchantInfoApplicationService.rotateJwtKey(merchantId));
        }
        if (keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY || keyType == OpenApiKeyType.PLATFORM_PAYLOAD_KEY) {
            return success(merchantInfoApplicationService.rotatePlatformPayloadKey(merchantId));
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY || keyType == OpenApiKeyType.MERCHANT_RESPONSE_KEY) {
            return success(merchantInfoApplicationService.rotateMerchantResponseKey(merchantId));
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "keyType must be JWT_KEY, PLATFORM_PAYLOAD_KEY or MERCHANT_RESPONSE_KEY");
    }

    /**
     * 查询管理端商户 OpenAPI 密钥操作记录。仅返回脱敏审计字段，不返回请求或响应正文。
     *
     * @param merchantId 商户号
     * @param request    查询条件
     * @return 商户 OpenAPI 密钥操作记录
     */
    @PostMapping("/{merchantId}/logs")
    @RequiresPermission("merchant:material:logs")
    public CommonResult<PageResult<SysOperLogDTO>> logs(@PathVariable("merchantId") String merchantId,
                                                        @RequestBody(required = false) SysOperLogQueryRequest request) {
        SysOperLogQueryRequest query = request == null ? new SysOperLogQueryRequest() : request;
        query.setMerchantId(merchantId);
        query.setModuleName(OPENAPI_KEY_MODULE_NAME);
        return success(adminOperLogApplicationService.pageOperLogs(query));
    }

    private void requirePrivateMaterialPermission(OpenApiKeyType keyType) {
        if (!keyAuditService.isPrivateMaterial(keyType)) {
            return;
        }
        InternalAuthAccount account = InternalAuthContextHolder.get();
        List<String> permissions = account == null ? List.of() : account.getPermissions();
        if (!permissions.contains("*:*:*") && !permissions.contains("merchant:material:private")) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "缺少私钥材料导出权限");
        }
    }

    private ResponseEntity<byte[]> toDownloadResponse(OpenApiKeyDownloadFile file) {
        String encodedFileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(file.getContent());
    }
}
