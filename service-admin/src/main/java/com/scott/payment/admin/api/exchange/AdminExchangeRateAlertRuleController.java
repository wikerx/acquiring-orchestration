package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportResult;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleSaveRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 管理端汇率波动预警规则接口。 */
@RestController
@RequestMapping("/admin/exchange/alert-rules")
public class AdminExchangeRateAlertRuleController {

    private final AdminExchangeRateVolatilityApplicationService applicationService;

    /** 创建按操作权限隔离的汇率预警规则接口。 */
    public AdminExchangeRateAlertRuleController(AdminExchangeRateVolatilityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 分页查询系统基线和自定义规则。 */
    @PostMapping("/search")
    @RequiresPermission("exchange:alert-rule:list")
    public CommonResult<PageResult<AlertRuleResponse>> page(@RequestBody(required = false) @Valid AlertRuleQuery request) {
        return success(applicationService.pageRules(request));
    }

    /** 查询规则完整配置和审计字段。 */
    @GetMapping("/{id}")
    @RequiresPermission("exchange:alert-rule:query")
    public CommonResult<AlertRuleResponse> detail(@PathVariable("id") Long id) {
        return success(applicationService.getRule(id));
    }

    /** 新增自定义规则；系统基线规则只能由受控迁移创建。 */
    @PostMapping
    @RequiresPermission("exchange:alert-rule:add")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.CREATE, operation = "新增汇率预警规则")
    public CommonResult<AlertRuleResponse> create(@Valid @RequestBody AlertRuleSaveRequest request) {
        return success(applicationService.createRule(request));
    }

    /** 修改规则；领域服务负责保护系统基线阈值和范围。 */
    @PutMapping("/{id}")
    @RequiresPermission("exchange:alert-rule:edit")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.UPDATE, operation = "修改汇率预警规则")
    public CommonResult<AlertRuleResponse> update(@PathVariable("id") Long id,
                                                  @Valid @RequestBody AlertRuleSaveRequest request) {
        return success(applicationService.updateRule(id, request));
    }

    /** 删除自定义规则，系统强制规则始终拒绝删除。 */
    @DeleteMapping("/{id}")
    @RequiresPermission("exchange:alert-rule:remove")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.DELETE, operation = "删除汇率预警规则")
    public CommonResult<Void> delete(@PathVariable("id") Long id) {
        applicationService.deleteRule(id);
        return success();
    }

    /** 启用一条有效规则。 */
    @PutMapping("/{id}/enable")
    @RequiresPermission("exchange:alert-rule:start")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.UPDATE, operation = "启用汇率预警规则")
    public CommonResult<AlertRuleResponse> enable(@PathVariable("id") Long id) {
        return success(applicationService.changeRuleStatus(id, true));
    }

    /** 停用自定义规则，系统强制规则始终拒绝停用。 */
    @PutMapping("/{id}/disable")
    @RequiresPermission("exchange:alert-rule:stop")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.UPDATE, operation = "停用汇率预警规则")
    public CommonResult<AlertRuleResponse> disable(@PathVariable("id") Long id) {
        return success(applicationService.changeRuleStatus(id, false));
    }

    /** 导入 Excel 自定义规则；任一行失败时整批不落库。 */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @RequiresPermission("exchange:alert-rule:import")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.CREATE, operation = "导入汇率预警规则")
    public CommonResult<AlertRuleImportResult> importRules(@RequestPart("file") MultipartFile file) {
        return success(applicationService.importRules(file));
    }

    /** 按当前筛选条件导出规则配置。 */
    @PostMapping("/export")
    @RequiresPermission("exchange:alert-rule:export")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率预警规则")
    public void export(@RequestBody(required = false) AlertRuleQuery request, HttpServletResponse response) {
        applicationService.exportRules(request, currentOperator(), response);
    }

    /** 从已认证上下文取得导出审计操作人。 */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null || account.getLoginAccount() == null ? "admin" : account.getLoginAccount();
    }
}
