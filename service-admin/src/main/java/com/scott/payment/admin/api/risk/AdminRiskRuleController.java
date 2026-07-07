package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.risk.RiskDTOs;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskRuleController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 内风控规则管理接口，位于 service-admin 接口层，仅维护管理端规则配置。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskRuleController {

    private static final String MODULE_TYPE = "RULE";

    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    public AdminRiskRuleController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 分页查询内风控规则。
     *
     * @param functionCode 功能编码
     * @param request      查询条件
     * @return 规则分页结果
     */
    @PostMapping("/rule/{functionCode}/page")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询风控规则")
    public CommonResult<PageResult<RiskDTOs.RiskRecordResponse>> page(@PathVariable("functionCode") String functionCode,
                                                                      @RequestBody(required = false) RiskDTOs.RiskRuleQueryRequest request) {
        return success(riskManagementApplicationService.pageRules(functionCode, request));
    }

    /**
     * 查询内风控规则详情。
     *
     * @param functionCode 功能编码
     * @param id           规则ID
     * @return 规则详情
     */
    @GetMapping("/rule/{functionCode}/{id}")
    @RequiresPermission("risk:rule:list")
    public CommonResult<RiskDTOs.RiskRecordResponse> detail(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id) {
        return success(riskManagementApplicationService.ruleDetail(functionCode, id));
    }

    /**
     * 新增内风控规则。
     *
     * @param functionCode 功能编码
     * @param request      保存请求
     * @return 新增后的规则
     */
    @PostMapping("/rule/{functionCode}")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.CREATE, operation = "新增风控规则")
    public CommonResult<RiskDTOs.RiskRecordResponse> create(@PathVariable("functionCode") String functionCode,
                                                           @Valid @RequestBody RiskDTOs.RiskRuleSaveRequest request) {
        return success(riskManagementApplicationService.createRule(functionCode, request));
    }

    /**
     * 修改内风控规则。
     *
     * @param functionCode 功能编码
     * @param id           规则ID
     * @param request      保存请求
     * @return 修改后的规则
     */
    @PutMapping("/rule/{functionCode}/{id}")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.UPDATE, operation = "修改风控规则")
    public CommonResult<RiskDTOs.RiskRecordResponse> update(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id,
                                                           @Valid @RequestBody RiskDTOs.RiskRuleSaveRequest request) {
        return success(riskManagementApplicationService.updateRule(functionCode, id, request));
    }

    /**
     * 删除内风控规则。
     *
     * @param functionCode 功能编码
     * @param id           规则ID
     * @return 空结果
     */
    @DeleteMapping("/RULE/{functionCode}/{id}")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.DELETE, operation = "删除风控规则")
    public CommonResult<Void> remove(@PathVariable("functionCode") String functionCode,
                                     @PathVariable("id") Long id) {
        riskManagementApplicationService.remove(MODULE_TYPE, functionCode, id);
        return success();
    }

    /**
     * 批量删除内风控规则。
     *
     * @param functionCode 功能编码
     * @param request      批量删除请求
     * @return 空结果
     */
    @DeleteMapping("/RULE/{functionCode}/batch")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.DELETE, operation = "批量删除风控规则")
    public CommonResult<Void> batchRemove(@PathVariable("functionCode") String functionCode,
                                          @RequestBody RiskDTOs.BatchRemoveRequest request) {
        riskManagementApplicationService.batchRemove(MODULE_TYPE, functionCode, request);
        return success();
    }

    /**
     * 更新内风控规则状态。
     *
     * @param functionCode 功能编码
     * @param id           规则ID
     * @param request      状态更新请求
     * @return 更新后的规则
     */
    @PutMapping("/RULE/{functionCode}/{id}/status")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.UPDATE, operation = "更新风控规则状态")
    public CommonResult<RiskDTOs.RiskRecordResponse> updateStatus(@PathVariable("functionCode") String functionCode,
                                                                 @PathVariable("id") Long id,
                                                                 @RequestBody RiskDTOs.StatusUpdateRequest request) {
        return success(riskManagementApplicationService.updateStatus(MODULE_TYPE, functionCode, id, request));
    }

    /**
     * 导出内风控规则。
     *
     * @param functionCode 功能编码
     * @param response     HTTP 响应
     */
    @PostMapping("/RULE/{functionCode}/export")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.EXPORT, operation = "导出风控规则")
    public void export(@PathVariable("functionCode") String functionCode, HttpServletResponse response) {
        riskManagementApplicationService.export(MODULE_TYPE, functionCode, response);
    }

    /**
     * 下载内风控规则导入模板。
     *
     * @param functionCode 功能编码
     * @param response     HTTP 响应
     */
    @GetMapping("/RULE/{functionCode}/template")
    @RequiresPermission("risk:rule:list")
    public void template(@PathVariable("functionCode") String functionCode, HttpServletResponse response) {
        riskManagementApplicationService.template(MODULE_TYPE, functionCode, response);
    }

    /**
     * 导入内风控规则 CSV 文件。
     *
     * @param functionCode 功能编码
     * @param file         CSV 文件
     * @return 导入结果
     */
    @PostMapping("/RULE/{functionCode}/import")
    @RequiresPermission("risk:rule:list")
    @OperationLog(moduleName = "收单风控-规则管理", businessType = OperationTypeConstants.CREATE, operation = "导入风控规则")
    public CommonResult<RiskDTOs.ImportResultResponse> importCsv(@PathVariable("functionCode") String functionCode,
                                                                 @RequestParam("file") MultipartFile file) {
        return success(riskManagementApplicationService.importCsv(MODULE_TYPE, functionCode, file));
    }
}
