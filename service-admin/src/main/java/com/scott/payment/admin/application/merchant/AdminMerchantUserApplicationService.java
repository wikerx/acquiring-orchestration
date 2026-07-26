package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.admin.service.AdminMerchantUserService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserApplicationService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantUserApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantUserApplicationService {

    /**
     * admin Merchant User Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminMerchantUserService adminMerchantUserService;

    /**
     * 创建 AdminMerchantUserApplicationService 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param adminMerchantUserService admin Merchant User Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminMerchantUserApplicationService(AdminMerchantUserService adminMerchantUserService) {
        this.adminMerchantUserService = adminMerchantUserService;
    }

    /**
     * 编排 page Merchant Users 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request) {
        return adminMerchantUserService.pageMerchantUsers(request);
    }

    /**
     * 编排 get Merchant User 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param accountId account Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public AdminMerchantUserDetailDTO getMerchantUser(Long accountId) {
        return adminMerchantUserService.getMerchantUser(accountId);
    }
}
