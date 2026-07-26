package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskCommonController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控公共查询接口，位于 service-admin 接口层，仅提供管理端功能定义和基础下拉选项。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskCommonController {

    /**
     * risk Management Application Service 依赖，用于 Admin Risk Common Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    /**
     * 创建风控公共查询接口。
     *
     * @param riskManagementApplicationService 风控管理应用服务
     */
    public AdminRiskCommonController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 查询管理端支持的风控功能定义。
     *
     * @return 风控功能定义列表
     */
    @GetMapping("/functions")
    @RequiresPermission("risk:access")
    public CommonResult<List<RiskDTOs.FunctionDefinitionResponse>> functions() {
        return success(riskManagementApplicationService.functions());
    }

    /**
     * 查询风控页面下拉选项，优先复用系统字典和基础数据。
     *
     * @return 页面下拉选项
     */
    @GetMapping("/options")
    @RequiresPermission("risk:access")
    public CommonResult<RiskDTOs.RiskOptionsResponse> options() {
        return success(riskManagementApplicationService.options());
    }
}
