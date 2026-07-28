package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateBatchSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.GenerateBusinessRateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.StatusRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.VoidRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
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
 * @classname : AdminExchangeRateController
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Admin Exchange Rate Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminExchangeRateController {

    /**
     * application Service 依赖，用于 Admin Exchange Rate Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminExchangeRateApplicationService applicationService;

    /**
     * 整理adminexchange汇率controller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param applicationService application Service 输入值，参与 applicationservice 的查询、校验、转换、写入或日志摘要
     */
    public AdminExchangeRateController(AdminExchangeRateApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询汇率源原始报价。
     *
     * @param query 原始汇率查询条件，允许为空
     * @return 原始汇率分页结果
     */
    @PostMapping("/raw-rates/search")
    @RequiresPermission("exchange:raw-rate:list")
    public CommonResult<PageResult<RawRateResponse>> pageRawRates(@RequestBody(required = false) RawRateQuery query) {
        return success(applicationService.pageRawRates(query));
    }

    /**
     * 导出原始汇率记录。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/raw-rates/export")
    @RequiresPermission("exchange:raw-rate:export")
    @OperationLog(moduleName = "原始汇率记录", businessType = OperationTypeConstants.EXPORT, operation = "导出原始汇率")
    public void exportRawRates(@RequestBody(required = false) RawRateQuery query,
                               HttpServletResponse response) {
        applicationService.exportRawRates(query, currentOperatorName(), response);
    }

    /**
     * 查询原始汇率详情。
     *
     * @param id 原始汇率主键
     * @return 原始汇率详情
     */
    @GetMapping("/raw-rates/{id}")
    @RequiresPermission("exchange:raw-rate:detail")
    public CommonResult<RawRateResponse> getRawRate(@PathVariable("id") Long id) {
        return success(applicationService.getRawRate(id));
    }

    /**
     * 手工录入原始汇率。
     *
     * @param request 原始汇率保存请求
     * @return 新增后的原始汇率详情
     */
    @PostMapping("/raw-rates")
    @RequiresPermission("exchange:raw-rate:add")
    @OperationLog(moduleName = "原始汇率记录", businessType = OperationTypeConstants.CREATE, operation = "新增手工原始汇率")
    public CommonResult<RawRateResponse> createManualRawRate(@Valid @RequestBody RawRateSaveRequest request) {
        return success(applicationService.createManualRawRate(request));
    }

    /**
     * 作废未生成业务汇率的原始报价。
     *
     * @param id      原始汇率主键
     * @param request 作废请求，必须提供作废原因
     * @return 作废后的原始汇率详情
     */
    @PutMapping("/raw-rates/{id}/void")
    @RequiresPermission("exchange:raw-rate:void")
    @OperationLog(moduleName = "原始汇率记录", businessType = OperationTypeConstants.UPDATE, operation = "作废原始汇率")
    public CommonResult<RawRateResponse> voidRawRate(@PathVariable("id") Long id,
                                                     @Valid @RequestBody VoidRequest request) {
        return success(applicationService.voidRawRate(id, request.getVoidReason()));
    }

    /**
     * 分页查询汇率生成规则。
     *
     * @param query 规则查询条件，允许为空
     * @return 汇率规则分页结果
     */
    @PostMapping("/rules/search")
    @RequiresPermission("exchange:rule:list")
    public CommonResult<PageResult<RuleResponse>> pageRules(@RequestBody(required = false) RuleQuery query) {
        return success(applicationService.pageRules(query));
    }

    /**
     * 导出汇率规则配置。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/rules/export")
    @RequiresPermission("exchange:rule:export")
    @OperationLog(moduleName = "汇率规则配置", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率规则")
    public void exportRules(@RequestBody(required = false) RuleQuery query,
                            HttpServletResponse response) {
        applicationService.exportRules(query, currentOperatorName(), response);
    }

    /**
     * 查询汇率规则详情。
     *
     * @param id 规则主键
     * @return 汇率规则详情
     */
    @GetMapping("/rules/{id}")
    @RequiresPermission("exchange:rule:detail")
    public CommonResult<RuleResponse> getRule(@PathVariable("id") Long id) {
        return success(applicationService.getRule(id));
    }

    /**
     * 新增业务汇率生成规则。
     *
     * @param request 规则保存请求
     * @return 新增后的规则详情
     */
    @PostMapping("/rules")
    @RequiresPermission("exchange:rule:add")
    @OperationLog(moduleName = "汇率规则配置", businessType = OperationTypeConstants.CREATE, operation = "新增汇率规则")
    public CommonResult<RuleResponse> createRule(@Valid @RequestBody RuleSaveRequest request) {
        return success(applicationService.createRule(request));
    }

    /**
     * 修改业务汇率生成规则。
     *
     * @param id      规则主键
     * @param request 规则保存请求
     * @return 修改后的规则详情
     */
    @PutMapping("/rules/{id}")
    @RequiresPermission("exchange:rule:edit")
    @OperationLog(moduleName = "汇率规则配置", businessType = OperationTypeConstants.UPDATE, operation = "修改汇率规则")
    public CommonResult<RuleResponse> updateRule(@PathVariable("id") Long id,
                                                 @Valid @RequestBody RuleSaveRequest request) {
        return success(applicationService.updateRule(id, request));
    }

    /**
     * 启用或停用汇率规则。
     *
     * @param id      规则主键
     * @param request 状态请求，1 表示启用，0 表示停用
     * @return 切换状态后的规则详情
     */
    @PutMapping("/rules/{id}/status")
    @RequiresPermission("exchange:rule:status")
    @OperationLog(moduleName = "汇率规则配置", businessType = OperationTypeConstants.UPDATE, operation = "切换汇率规则状态")
    public CommonResult<RuleResponse> updateRuleStatus(@PathVariable("id") Long id,
                                                       @Valid @RequestBody StatusRequest request) {
        return success(applicationService.updateRuleStatus(id, request.getStatus()));
    }

    /**
     * 分页查询最终业务汇率。
     *
     * @param query 业务汇率查询条件，允许为空
     * @return 业务汇率分页结果
     */
    @PostMapping("/business-rates/search")
    @RequiresPermission("exchange:business-rate:list")
    public CommonResult<PageResult<BusinessRateResponse>> pageBusinessRates(@RequestBody(required = false) BusinessRateQuery query) {
        return success(applicationService.pageBusinessRates(query));
    }

    /**
     * 导出最终业务汇率。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/business-rates/export")
    @RequiresPermission("exchange:business-rate:export")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.EXPORT, operation = "导出业务汇率")
    public void exportBusinessRates(@RequestBody(required = false) BusinessRateQuery query,
                                    HttpServletResponse response) {
        applicationService.exportBusinessRates(query, currentOperatorName(), response);
    }

    /**
     * 查询业务汇率详情。
     *
     * @param id 业务汇率主键
     * @return 业务汇率详情
     */
    @GetMapping("/business-rates/{id}")
    @RequiresPermission("exchange:business-rate:detail")
    public CommonResult<BusinessRateResponse> getBusinessRate(@PathVariable("id") Long id) {
        return success(applicationService.getBusinessRate(id));
    }

    /**
     * 手工新增可直接使用的业务汇率。
     *
     * @param request 业务汇率保存请求
     * @return 新增后的业务汇率详情
     */
    @PostMapping("/business-rates")
    @RequiresPermission("exchange:business-rate:add")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.CREATE, operation = "新增业务汇率")
    public CommonResult<BusinessRateResponse> createManualBusinessRate(@Valid @RequestBody BusinessRateSaveRequest request) {
        return success(applicationService.createManualBusinessRate(request));
    }

    /**
     * 批量手工新增可直接使用的业务汇率。
     *
     * @param request 批量保存请求
     * @return 新增后的业务汇率列表
     */
    @PostMapping("/business-rates/batch")
    @RequiresPermission("exchange:business-rate:batch")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.CREATE, operation = "批量录入业务汇率")
    public CommonResult<java.util.List<BusinessRateResponse>> createManualBusinessRates(@Valid @RequestBody BusinessRateBatchSaveRequest request) {
        return success(applicationService.createManualBusinessRates(request));
    }

    /**
     * 根据原始汇率和规则生成最终业务汇率。
     *
     * @param request 业务汇率生成请求
     * @return 生成后的业务汇率详情
     */
    @PostMapping("/business-rates/generate")
    @RequiresPermission("exchange:business-rate:generate")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.CREATE, operation = "生成业务汇率")
    public CommonResult<BusinessRateResponse> generateBusinessRate(@Valid @RequestBody GenerateBusinessRateRequest request) {
        return success(applicationService.generateBusinessRate(request));
    }

    /**
     * 启用或停用业务汇率。
     *
     * @param id      业务汇率主键
     * @param request 状态请求，1 表示启用，0 表示停用
     * @return 切换状态后的业务汇率详情
     */
    @PutMapping("/business-rates/{id}/status")
    @RequiresPermission("exchange:business-rate:status")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.UPDATE, operation = "切换业务汇率状态")
    public CommonResult<BusinessRateResponse> updateBusinessRateStatus(@PathVariable("id") Long id,
                                                                       @Valid @RequestBody StatusRequest request) {
        return success(applicationService.updateBusinessRateStatus(id, request.getStatus()));
    }

    /**
     * 分页查询汇率使用快照。
     *
     * @param query 快照查询条件，允许为空
     * @return 使用快照分页结果
     */
    @PostMapping("/usage-snapshots/search")
    @RequiresPermission("exchange:usage-snapshot:list")
    public CommonResult<PageResult<UsageSnapshotResponse>> pageUsageSnapshots(@RequestBody(required = false) UsageSnapshotQuery query) {
        return success(applicationService.pageUsageSnapshots(query));
    }

    /**
     * 导出汇率使用快照。
     *
     * @param query    查询条件，允许为空
     * @param response HTTP 响应
     */
    @PostMapping("/usage-snapshots/export")
    @RequiresPermission("exchange:usage-snapshot:export")
    @OperationLog(moduleName = "汇率使用快照", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率使用快照")
    public void exportUsageSnapshots(@RequestBody(required = false) UsageSnapshotQuery query,
                                     HttpServletResponse response) {
        applicationService.exportUsageSnapshots(query, currentOperatorName(), response);
    }

    /**
     * 查询汇率使用快照详情。
     *
     * @param id 快照主键
     * @return 使用快照详情
     */
    @GetMapping("/usage-snapshots/{id}")
    @RequiresPermission("exchange:usage-snapshot:detail")
    public CommonResult<UsageSnapshotResponse> getUsageSnapshot(@PathVariable("id") Long id) {
        return success(applicationService.getUsageSnapshot(id));
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
