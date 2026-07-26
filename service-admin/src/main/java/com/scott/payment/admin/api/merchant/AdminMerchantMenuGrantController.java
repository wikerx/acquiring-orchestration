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
 * @description : AdminMerchantMenuGrantController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantMenuGrantController {

    /**
     * admin Merchant Menu Grant Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
/**
 * 写入或更新 save Grant 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<Void> saveGrant(@PathVariable("merchantId") String merchantId,
                                        @Valid @RequestBody AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantApplicationService.saveGrant(merchantId, request);
        return success();
    }
}
