package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.admin.service.AdminUserMfaService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaApplicationService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 后台用户 MFA 应用服务，位于 service-admin 应用编排层；承接用户管理页面的 OTP 管理用例并调用领域服务。
 * @status : create
 */
@Service
public class AdminUserMfaApplicationService {

    /**
     * admin User Mfa Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminUserMfaService adminUserMfaService;

    /**
     * 创建后台用户 MFA 应用服务。
     *
     * @param adminUserMfaService MFA 领域服务
     */
    public AdminUserMfaApplicationService(AdminUserMfaService adminUserMfaService) {
        this.adminUserMfaService = adminUserMfaService;
    }

    /**
     * 编排 require Mfa 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse requireMfa(UserMfaActionRequest request) {
        return adminUserMfaService.requireMfa(request);
    }

    /**
     * 编排 reset Mfa 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse resetMfa(UserMfaActionRequest request) {
        return adminUserMfaService.resetMfa(request);
    }

    /**
     * 编排 exempt Mfa 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse exemptMfa(UserMfaExemptRequest request) {
        return adminUserMfaService.exemptMfa(request);
    }

    /**
     * 编排 disable Mfa 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse disableMfa(UserMfaActionRequest request) {
        return adminUserMfaService.disableMfa(request);
    }

    /**
     * 编排 unlock Mfa 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse unlockMfa(UserMfaActionRequest request) {
        return adminUserMfaService.unlockMfa(request);
    }

    /**
     * 编排 resend Bind Mail 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public UserMfaStatusResponse resendBindMail(UserMfaActionRequest request) {
        return adminUserMfaService.resendBindMail(request);
    }

    /**
     * 编排 page Logs 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminUserMfaApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    public PageResult<UserMfaLogResponse> pageLogs(UserMfaLogQuery query) {
        return adminUserMfaService.pageLogs(query);
    }
}
