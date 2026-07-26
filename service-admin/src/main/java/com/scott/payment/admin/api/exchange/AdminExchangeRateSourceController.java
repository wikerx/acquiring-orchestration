package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.StatusRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/exchange")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateSourceController
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : AdminExchangeRateSourceController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminExchangeRateSourceController {

    /**
     * application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminExchangeRateApplicationService applicationService;

    /**
     * 创建 AdminExchangeRateSourceController 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminExchangeRateSourceController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param applicationService application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminExchangeRateSourceController(AdminExchangeRateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询汇率源配置，供汇率源管理页面展示。
     *
     * @param query 汇率源查询条件，允许为空
     * @return 汇率源分页结果
     */
    @PostMapping("/sources/search")
    @RequiresPermission("exchange:source:list")
    public CommonResult<PageResult<SourceResponse>> pageSources(@RequestBody(required = false) SourceQuery query) {
        return success(applicationService.pageSources(query));
    }

    /**
     * 导出汇率源配置。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/sources/export")
    @RequiresPermission("exchange:source:export")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率源")
    public void exportSources(@RequestBody(required = false) SourceQuery query,
                              HttpServletResponse response) {
        applicationService.exportSources(query, currentOperatorName(), response);
    }

    /**
     * 查询单个汇率源配置详情。
     *
     * @param id 汇率源主键
     * @return 汇率源详情
     */
    @GetMapping("/sources/{id}")
    @RequiresPermission("exchange:source:detail")
    public CommonResult<SourceResponse> getSource(@PathVariable("id") Long id) {
        return success(applicationService.getSource(id));
    }

    /**
     * 新增外部汇率源配置。
     *
     * @param request 汇率源保存请求
     * @return 新增后的汇率源详情
     */
    @PostMapping("/sources")
    @RequiresPermission("exchange:source:add")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.CREATE, operation = "新增汇率源")
    public CommonResult<SourceResponse> createSource(@Valid @RequestBody SourceSaveRequest request) {
        return success(applicationService.createSource(request));
    }

    /**
     * 修改外部汇率源配置。
     *
     * @param id      汇率源主键
     * @param request 汇率源保存请求
     * @return 修改后的汇率源详情
     */
    @PutMapping("/sources/{id}")
    @RequiresPermission("exchange:source:edit")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.UPDATE, operation = "修改汇率源")
    public CommonResult<SourceResponse> updateSource(@PathVariable("id") Long id,
                                                     @Valid @RequestBody SourceSaveRequest request) {
        return success(applicationService.updateSource(id, request));
    }

    /**
     * 启用或停用汇率源。
     *
     * @param id      汇率源主键
     * @param request 状态请求，1 表示启用，0 表示停用
     * @return 切换状态后的汇率源详情
     */
    @PutMapping("/sources/{id}/status")
    @RequiresPermission("exchange:source:status")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.UPDATE, operation = "切换汇率源状态")
    public CommonResult<SourceResponse> updateSourceStatus(@PathVariable("id") Long id,
                                                           @Valid @RequestBody StatusRequest request) {
        return success(applicationService.updateSourceStatus(id, request.getStatus()));
    }

    /**
     * 删除未被原始汇率、规则或业务汇率引用的汇率源。
     *
     * @param id 汇率源主键
     * @return 空结果
     */
    @DeleteMapping("/sources/{id}")
    @RequiresPermission("exchange:source:remove")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.DELETE, operation = "删除汇率源")
    public CommonResult<Void> deleteSource(@PathVariable("id") Long id) {
        applicationService.deleteSource(id);
        return success();
    }

    /**
     * 获取当前操作人名称，用于写入 Excel 导出元信息。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
