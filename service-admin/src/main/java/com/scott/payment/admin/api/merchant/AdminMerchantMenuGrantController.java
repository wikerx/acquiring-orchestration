package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantMenuGrantApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/merchant-menu-grants")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Admin Merchant Menu Grant Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminMerchantMenuGrantController {

    /**
     * admin Merchant Menu Grant Application Service 依赖，用于 Admin Merchant Menu Grant Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminMerchantMenuGrantApplicationService adminMerchantMenuGrantApplicationService;

    /**
     * 创建商户菜单授权接口。
     *
     * @param adminMerchantMenuGrantApplicationService 商户菜单授权应用服务
     */
    public AdminMerchantMenuGrantController(AdminMerchantMenuGrantApplicationService adminMerchantMenuGrantApplicationService) {
        this.adminMerchantMenuGrantApplicationService = adminMerchantMenuGrantApplicationService;
    }

    /**
     * 查询商户菜单授权。
     *
     * @param merchantId 商户号
     * @return 授权信息
     */
    @GetMapping("/{merchantId}")
    @RequiresPermission("merchant:menu-grant:list")
    public CommonResult<AdminMerchantMenuGrantQueryResponse> queryGrant(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantMenuGrantApplicationService.queryGrant(merchantId));
    }

    /**
     * 保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    授权保存请求
     * @return 空响应
     */
    @PostMapping("/{merchantId}")
    @RequiresPermission("merchant:menu-grant:save")
    @OperationLog(moduleName = "商户菜单授权", businessType = OperationTypeConstants.UPDATE,
            operation = "保存商户菜单授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> saveGrant(@PathVariable("merchantId") String merchantId,
                                        @Valid @RequestBody AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantApplicationService.saveGrant(merchantId, request);
        return success();
    }
}
