package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.admin.service.AdminMerchantInfoService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoApplicationService
 * @date : 2026-06-19 21:19
 * @email : scott_x@163.com
 * @description : 商户接入资料应用服务
 * @status : create
 *
 * <p>负责承接管理后台商户管理入口，把商户资料维护与 OpenAPI 密钥材料编排统一收敛到应用层，
 * 避免控制器直接触达领域服务细节。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Info Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMerchantInfoApplicationService {

    /**
     * 商户接入资料领域服务。
     */
    private final AdminMerchantInfoService adminMerchantInfoService;

    /**
     * 创建商户接入资料应用服务。
     *
     * @param adminMerchantInfoService 商户接入资料领域服务
     */
    public AdminMerchantInfoApplicationService(AdminMerchantInfoService adminMerchantInfoService) {
        this.adminMerchantInfoService = adminMerchantInfoService;
    }

    /**
     * 查询商户新增和编辑表单选项。
     *
     * @return 表单选项
     */
    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantFormOptionsDTO getFormOptions() {
        return adminMerchantInfoService.getFormOptions();
    }

    /**
     * 分页查询商户。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<AdminMerchantInfoDTO> pageMerchants(AdminMerchantQueryRequest request) {
        return adminMerchantInfoService.pageMerchants(request);
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
    public AdminMerchantInfoDTO getMerchant(Long id) {
        return adminMerchantInfoService.getMerchant(id);
    }

    /**
     * 新增商户。
     *
     * @param request 商户保存请求
     * @return 商户详情
     */
    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request) {
        return adminMerchantInfoService.createMerchant(request);
    }

    /**
     * 更新商户。
     *
     * @param id      商户主键
     * @param request 商户保存请求
     * @return 商户详情
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantInfoDTO updateMerchant(Long id, AdminMerchantSaveRequest request) {
        return adminMerchantInfoService.updateMerchant(id, request);
    }

    /**
     * 更新商户状态。
     *
     * @param id             商户主键
     * @param merchantStatus 商户状态
     * @return 商户详情
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param merchantStatus 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantInfoDTO updateStatus(Long id, Integer merchantStatus) {
        return adminMerchantInfoService.updateStatus(id, merchantStatus);
    }

    /**
     * 初始化商户安全材料。
     *
     * @param merchantId 商户号
     * @return 安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(String merchantId) {
        return adminMerchantInfoService.provisionSecurityMaterial(merchantId);
    }

    /**
     * 查询商户密钥材料。
     *
     * @param merchantId 商户号
     * @return 密钥材料集合
     */
    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantKeyBundleDTO getMerchantKeys(String merchantId) {
        return adminMerchantInfoService.getMerchantKeys(merchantId);
    }

    /**
     * 轮换商户 JWT 密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantSecurityMaterialDTO rotateJwtKey(String merchantId) {
        return adminMerchantInfoService.rotateJwtKey(merchantId);
    }

    /**
     * 轮换平台请求体 RSA 密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(String merchantId) {
        return adminMerchantInfoService.rotatePlatformPayloadKey(merchantId);
    }

    /**
     * 轮换商户响应 RSA 密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(String merchantId) {
        return adminMerchantInfoService.rotateMerchantResponseKey(merchantId);
    }

    /**
     * 更新商户响应公钥。
     *
     * @param merchantId 商户号
     * @param request    响应公钥更新请求
     * @return 商户详情
     */
    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantInfoDTO updateMerchantResponseKey(String merchantId, AdminMerchantResponseKeyRequest request) {
        return adminMerchantInfoService.updateMerchantResponseKey(merchantId, request);
    }
}
