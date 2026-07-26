package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantUserApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/merchant-users")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantUserController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantUserController {

    /**
     * admin Merchant User Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminMerchantUserApplicationService adminMerchantUserApplicationService;

    /**
     * 创建 AdminMerchantUserController 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param adminMerchantUserApplicationService admin Merchant User Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminMerchantUserController(AdminMerchantUserApplicationService adminMerchantUserApplicationService) {
        this.adminMerchantUserApplicationService = adminMerchantUserApplicationService;
    }

    @GetMapping
    @RequiresPermission("admin:merchant:user:list")
    public CommonResult<PageResult<AdminMerchantUserListDTO>> pageMerchantUsers(@ModelAttribute AdminMerchantUserQueryRequest request) {
        return success(adminMerchantUserApplicationService.pageMerchantUsers(request));
    }

    @GetMapping("/{accountId}")
    @RequiresPermission("admin:merchant:user:detail")
    public CommonResult<AdminMerchantUserDetailDTO> getMerchantUser(@PathVariable("accountId") Long accountId) {
        return success(adminMerchantUserApplicationService.getMerchantUser(accountId));
    }
}
