package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
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
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.SysOperLogDTO;
import com.scott.payment.merchant.dto.SysOperLogQueryRequest;
import com.scott.payment.merchant.service.MerchantOperLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
 * 商户端 OpenAPI 密钥管理接口，仅允许当前登录商户查看、复制和下载自己的接入材料。
 */
@RestController
@RequestMapping("/merchant/openapi/keys")
public class MerchantOpenApiKeyController {

    private static final String OPENAPI_KEY_MODULE_NAME = "商户OpenAPI密钥";

    private final OpenApiMerchantKeyMaterialService keyMaterialService;
    private final OpenApiKeyAuditService keyAuditService;
    private final MerchantOperLogService merchantOperLogService;

    /**
     * 创建商户端 OpenAPI 密钥管理接口。
     *
     * @param keyMaterialService    密钥材料统一服务
     * @param keyAuditService       密钥审计辅助服务
     * @param merchantOperLogService 商户操作日志服务
     */
    public MerchantOpenApiKeyController(OpenApiMerchantKeyMaterialService keyMaterialService,
                                        OpenApiKeyAuditService keyAuditService,
                                        MerchantOperLogService merchantOperLogService) {
        this.keyMaterialService = keyMaterialService;
        this.keyAuditService = keyAuditService;
        this.merchantOperLogService = merchantOperLogService;
    }

    /**
     * 查询当前商户 OpenAPI 对接材料概要。
     *
     * @return 当前商户对接材料概要
     */
    @GetMapping
    @RequiresPermission("merchant:openapi:key:view")
    public CommonResult<OpenApiMerchantKeyMaterialVO> getMaterial() {
        return success(keyMaterialService.queryMaterial(currentMerchantId()));
    }

    /**
     * 复制当前商户 OpenAPI 接入材料。私钥复制需要单独权限并记录操作日志。
     *
     * @param request 复制请求
     * @return 可复制文本
     */
    @PostMapping("/copy")
    @RequiresPermission("merchant:openapi:key:copy")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.EXPORT, operation = "复制OpenAPI接入材料")
    public CommonResult<OpenApiKeyCopyResponse> copy(@RequestBody OpenApiKeyExportRequest request) {
        requireCopyPermission(request == null ? null : request.getKeyType());
        return success(keyMaterialService.copy(currentMerchantId(), request));
    }

    /**
     * 下载当前商户 OpenAPI 接入材料文件。
     *
     * @param keyType 材料类型
     * @param format  下载格式
     * @return 附件文件
     */
    @GetMapping("/download")
    @RequiresPermission("merchant:openapi:key:download")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.EXPORT, operation = "下载OpenAPI接入材料")
    public ResponseEntity<byte[]> download(@RequestParam("keyType") OpenApiKeyType keyType,
                                           @RequestParam(value = "format", required = false) OpenApiKeyExportFormat format) {
        requireDownloadPermission(keyType);
        return toDownloadResponse(keyMaterialService.download(currentMerchantId(), keyType, format));
    }

    /**
     * 轮换当前商户 OpenAPI 密钥。
     *
     * @param request 轮换请求
     * @return 轮换后的密钥概要
     */
    @PostMapping("/rotate")
    @RequiresPermission("merchant:openapi:key:view")
    @OperationLog(moduleName = "商户OpenAPI密钥", businessType = OperationTypeConstants.UPDATE, operation = "轮换OpenAPI密钥")
    public CommonResult<OpenApiMerchantKeyMaterialVO> rotate(@RequestBody OpenApiKeyExportRequest request) {
        OpenApiKeyType keyType = request == null ? null : request.getKeyType();
        requireRotatePermission(keyType);
        return success(keyMaterialService.rotate(currentMerchantId(), keyType));
    }

    /**
     * 查询当前商户 OpenAPI 密钥操作记录。仅返回脱敏审计字段，不返回请求或响应正文。
     *
     * @param request 查询条件
     * @return 当前商户 OpenAPI 密钥操作记录
     */
    @PostMapping("/logs")
    @RequiresPermission("merchant:openapi:key:log")
    public CommonResult<PageResult<SysOperLogDTO>> logs(@RequestBody(required = false) SysOperLogQueryRequest request) {
        return success(queryLogs(request));
    }

    /**
     * 查询当前商户 OpenAPI 密钥操作记录。兼容前端 GET 分页调用。
     *
     * @param request 查询条件
     * @return 当前商户 OpenAPI 密钥操作记录
     */
    @GetMapping("/logs")
    @RequiresPermission("merchant:openapi:key:log")
    public CommonResult<PageResult<SysOperLogDTO>> getLogs(@ModelAttribute SysOperLogQueryRequest request) {
        return success(queryLogs(request));
    }

    private PageResult<SysOperLogDTO> queryLogs(SysOperLogQueryRequest request) {
        SysOperLogQueryRequest query = request == null ? new SysOperLogQueryRequest() : request;
        query.setMerchantId(currentMerchantId());
        query.setModuleName(OPENAPI_KEY_MODULE_NAME);
        return merchantOperLogService.pageOperLogs(query);
    }

    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    private void requireCopyPermission(OpenApiKeyType keyType) {
        rejectPlatformPrivateKey(keyType);
        if (keyAuditService.isPublicMaterial(keyType)) {
            return;
        }
        requirePermission("merchant:openapi:key:download-private", "缺少敏感材料复制权限");
    }

    private void requireDownloadPermission(OpenApiKeyType keyType) {
        rejectPlatformPrivateKey(keyType);
        if (keyAuditService.isPublicMaterial(keyType)) {
            return;
        }
        requirePermission("merchant:openapi:key:download-private", "缺少敏感材料下载权限");
    }

    private void requireRotatePermission(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.JWT_KEY) {
            requirePermission("merchant:openapi:key:rotate-jwt", "缺少 JWT 密钥轮换权限");
            return;
        }
        if (keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY) {
            requirePermission("merchant:openapi:key:rotate-response", "缺少响应密钥轮换权限");
            return;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户端仅允许轮换 JWT_KEY 或 MERCHANT_RESPONSE_PRIVATE_KEY");
    }

    private void requirePermission(String permission, String message) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        List<String> permissions = account == null ? List.of() : account.getPermissions();
        if (!permissions.contains("*:*:*") && !permissions.contains(permission)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), message);
        }
    }

    private void rejectPlatformPrivateKey(OpenApiKeyType keyType) {
        if (keyType == OpenApiKeyType.PLATFORM_PRIVATE_KEY) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "商户端不允许查看或导出平台请求解密私钥");
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
