package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantInfoApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantStatusRequest;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoController
 * @date : 2026-06-19 21:18
 * @email : scott_x@163.com
 * @description : 管理后台商户接入资料控制器
 * @status : create
 *
 * <p>商户管理菜单下的接口入口，负责商户基础资料、状态维护和 OpenAPI 安全材料管理的参数接收、
 * 权限校验与 HTTP 映射，具体业务编排由应用服务层处理。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Info 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchants")
public class AdminMerchantInfoController {

    /**
     * 商户接入资料应用服务。
     */
    private final AdminMerchantInfoApplicationService adminMerchantInfoApplicationService;

    /**
     * 创建商户接入资料控制器。
     *
     * @param adminMerchantInfoApplicationService 商户接入资料应用服务
     */
    public AdminMerchantInfoController(AdminMerchantInfoApplicationService adminMerchantInfoApplicationService) {
        this.adminMerchantInfoApplicationService = adminMerchantInfoApplicationService;
    }

    /**
     * 查询商户新增和编辑表单选项。
     *
     * @return 表单选项
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/form-options")
    @RequiresPermission("merchant:info:list")
    public CommonResult<AdminMerchantFormOptionsDTO> formOptions() {
        return success(adminMerchantInfoApplicationService.getFormOptions());
    }

    /**
     * 分页查询商户基础资料。
     *
     * @param request 查询条件
     * @return 商户分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:info:list")
    public CommonResult<PageResult<AdminMerchantInfoDTO>> pageMerchants(@RequestBody(required = false) AdminMerchantQueryRequest request) {
        return success(adminMerchantInfoApplicationService.pageMerchants(request));
    }

    /**
     * 查询单个商户详情。
     *
     * @param id 商户主键
     * @return 商户详情
     */
    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("merchant:info:query")
    public CommonResult<AdminMerchantInfoDTO> getMerchant(@PathVariable("id") Long id) {
        return success(adminMerchantInfoApplicationService.getMerchant(id));
    }

    /**
     * 新增商户基础资料。
     *
     * @param request 商户保存请求
     * @return 新增后的商户资料
     */
    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("merchant:info:add")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增商户")
    public CommonResult<AdminMerchantInfoDTO> createMerchant(@Valid @RequestBody AdminMerchantSaveRequest request) {
        return success(adminMerchantInfoApplicationService.createMerchant(request));
    }

    /**
     * 更新商户基础资料。
     *
     * @param id      商户主键
     * @param request 商户保存请求
     * @return 更新后的商户资料
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户")
    public CommonResult<AdminMerchantInfoDTO> updateMerchant(@PathVariable("id") Long id,
                                                             @Valid @RequestBody AdminMerchantSaveRequest request) {
        return success(adminMerchantInfoApplicationService.updateMerchant(id, request));
    }

    /**
     * 更新商户状态。
     *
     * @param id      商户主键
     * @param request 状态变更请求
     * @return 更新后的商户资料
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("merchant:info:changeStatus")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改商户状态")
    public CommonResult<AdminMerchantInfoDTO> updateStatus(@PathVariable("id") Long id,
                                                           @Valid @RequestBody AdminMerchantStatusRequest request) {
        return success(adminMerchantInfoApplicationService.updateStatus(id, request.getMerchantStatus()));
    }

    /**
     * 初始化商户 OpenAPI 对接材料。
     *
     * @param merchantId 商户号
     * @return 一次性安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{merchantId}/security-material/provision")
    @RequiresPermission("merchant:material:view")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "生成商户OpenAPI对接材料")
    public CommonResult<AdminMerchantSecurityMaterialDTO> provisionSecurityMaterial(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantInfoApplicationService.provisionSecurityMaterial(merchantId));
    }

    /**
     * 查询商户当前密钥概览。
     *
     * @param merchantId 商户号
     * @return 商户密钥集合
     */
    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{merchantId}/keys")
    @RequiresPermission("merchant:key:manage")
    public CommonResult<AdminMerchantKeyBundleDTO> getMerchantKeys(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantInfoApplicationService.getMerchantKeys(merchantId));
    }

    /**
     * 轮换商户 JWT 对称密钥。
     *
     * @param merchantId 商户号
     * @return 轮换后的安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{merchantId}/jwt-key/rotate")
    @RequiresPermission("merchant:key:rotate")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "轮换商户JWT密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotateJwtKey(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantInfoApplicationService.rotateJwtKey(merchantId));
    }

    /**
     * 轮换平台请求体加密密钥。
     *
     * @param merchantId 商户号
     * @return 轮换后的安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{merchantId}/platform-payload-key/rotate")
    @RequiresPermission("merchant:platform-payload-key:rotate")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "轮换平台请求体密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotatePlatformPayloadKey(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantInfoApplicationService.rotatePlatformPayloadKey(merchantId));
    }

    /**
     * 轮换商户响应密钥对。
     *
     * @param merchantId 商户号
     * @return 轮换后的安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{merchantId}/response-key/rotate")
    @RequiresPermission("merchant:response-key:update")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "生成商户响应密钥")
    public CommonResult<AdminMerchantSecurityMaterialDTO> rotateMerchantResponseKey(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantInfoApplicationService.rotateMerchantResponseKey(merchantId));
    }

    /**
     * 更新商户自管响应公钥。
     *
     * @param merchantId 商户号
     * @param request    响应公钥更新请求
     * @return 更新后的商户资料
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{merchantId}/response-key")
    @RequiresPermission("merchant:response-key:update")
    @OperationLog(moduleName = "商户信息管理", businessType = OperationTypeConstants.UPDATE, operation = "更新商户响应公钥")
    public CommonResult<AdminMerchantInfoDTO> updateMerchantResponseKey(@PathVariable("merchantId") String merchantId,
                                                                        @Valid @RequestBody AdminMerchantResponseKeyRequest request) {
        return success(adminMerchantInfoApplicationService.updateMerchantResponseKey(merchantId, request));
    }
}
