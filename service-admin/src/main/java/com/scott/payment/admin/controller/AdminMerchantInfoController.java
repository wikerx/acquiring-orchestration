package com.scott.payment.admin.controller;

import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantStatusRequest;
import com.scott.payment.admin.service.AdminMerchantInfoService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台商户 OpenAPI 接入资料控制器。
 */
@RestController
@RequestMapping("/admin/merchants")
public class AdminMerchantInfoController {

    private final AdminMerchantInfoService merchantInfoService;

    public AdminMerchantInfoController(AdminMerchantInfoService merchantInfoService) {
        this.merchantInfoService = merchantInfoService;
    }

    @PostMapping("/search")
    @RequiresPermission("merchant:info:list")
    public CommonResult<PageResult<AdminMerchantInfoDTO>> pageMerchants(@RequestBody(required = false) AdminMerchantQueryRequest request) {
        return success(merchantInfoService.pageMerchants(request));
    }

    @GetMapping("/{id}")
    @RequiresPermission("merchant:info:query")
    public CommonResult<AdminMerchantInfoDTO> getMerchant(@PathVariable("id") Long id) {
        return success(merchantInfoService.getMerchant(id));
    }

    @PostMapping
    @RequiresPermission("merchant:info:add")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户")
    public CommonResult<AdminMerchantInfoDTO> createMerchant(@Valid @RequestBody AdminMerchantSaveRequest request) {
        return success(merchantInfoService.createMerchant(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户")
    public CommonResult<AdminMerchantInfoDTO> updateMerchant(@PathVariable("id") Long id,
                                                             @Valid @RequestBody AdminMerchantSaveRequest request) {
        return success(merchantInfoService.updateMerchant(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("merchant:info:changeStatus")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户状态")
    public CommonResult<AdminMerchantInfoDTO> updateStatus(@PathVariable("id") Long id,
                                                           @Valid @RequestBody AdminMerchantStatusRequest request) {
        return success(merchantInfoService.updateStatus(id, request.getMerchantStatus()));
    }

    @PostMapping("/{merchantId}/security-material/provision")
    @RequiresPermission("merchant:material:view")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "生成商户OpenAPI对接材料")
    public CommonResult<AdminMerchantSecurityMaterialDTO> provisionSecurityMaterial(@PathVariable("merchantId") String merchantId) {
        return success(merchantInfoService.provisionSecurityMaterial(merchantId));
    }

    @GetMapping("/{merchantId}/keys")
    @RequiresPermission("merchant:key:manage")
    public CommonResult<AdminMerchantKeyBundleDTO> getMerchantKeys(@PathVariable("merchantId") String merchantId) {
        return success(merchantInfoService.getMerchantKeys(merchantId));
    }

    @PostMapping("/{merchantId}/jwt-key/rotate")
    @RequiresPermission("merchant:key:rotate")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "轮换商户JWT密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotateJwtKey(@PathVariable("merchantId") String merchantId) {
        return success(merchantInfoService.rotateJwtKey(merchantId));
    }

    @PostMapping("/{merchantId}/platform-payload-key/rotate")
    @RequiresPermission("merchant:platform-payload-key:rotate")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "轮换平台请求体密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotatePlatformPayloadKey(@PathVariable("merchantId") String merchantId) {
        return success(merchantInfoService.rotatePlatformPayloadKey(merchantId));
    }

    @PostMapping("/{merchantId}/response-key/rotate")
    @RequiresPermission("merchant:response-key:update")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "生成商户响应密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotateMerchantResponseKey(@PathVariable("merchantId") String merchantId) {
        return success(merchantInfoService.rotateMerchantResponseKey(merchantId));
    }

    @PutMapping("/{merchantId}/response-key")
    @RequiresPermission("merchant:response-key:update")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "更新商户响应公钥")
    public CommonResult<AdminMerchantInfoDTO> updateMerchantResponseKey(@PathVariable("merchantId") String merchantId,
                                                                        @Valid @RequestBody AdminMerchantResponseKeyRequest request) {
        return success(merchantInfoService.updateMerchantResponseKey(merchantId, request));
    }
}
