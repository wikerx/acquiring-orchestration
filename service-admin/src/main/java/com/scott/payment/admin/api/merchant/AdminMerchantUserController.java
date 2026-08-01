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
 * @description : Admin Merchant User Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminMerchantUserController {

    /**
     * admin Merchant User Application Service 依赖，用于 Admin Merchant User Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminMerchantUserApplicationService adminMerchantUserApplicationService;

    /**
     * 整理admin商户用户controller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param adminMerchantUserApplicationService admin Merchant User Application Service 输入值，参与 admin商户用户applicationservice 的查询、校验、转换、写入或日志摘要
     */
    public AdminMerchantUserController(AdminMerchantUserApplicationService adminMerchantUserApplicationService) {
        this.adminMerchantUserApplicationService = adminMerchantUserApplicationService;
    }

    /**
     * 分页查询商户后台用户，敏感认证字段不得进入列表响应。
     *
     * @param request 商户号、账号、状态和分页条件
     * @return 商户用户分页结果
     */
    @GetMapping
    @RequiresPermission("admin:merchant:user:list")
    public CommonResult<PageResult<AdminMerchantUserListDTO>> pageMerchantUsers(@ModelAttribute AdminMerchantUserQueryRequest request) {
        return success(adminMerchantUserApplicationService.pageMerchantUsers(request));
    }

    /**
     * 查询指定商户用户详情，密码、盐值和令牌等认证材料不对管理端返回。
     *
     * @param accountId 商户用户账号主键
     * @return 商户用户详情
     */
    @GetMapping("/{accountId}")
    @RequiresPermission("admin:merchant:user:detail")
    public CommonResult<AdminMerchantUserDetailDTO> getMerchantUser(@PathVariable("accountId") Long accountId) {
        return success(adminMerchantUserApplicationService.getMerchantUser(accountId));
    }
}
