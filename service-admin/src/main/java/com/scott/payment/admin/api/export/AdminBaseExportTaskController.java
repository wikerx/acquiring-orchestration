package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CardBinSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CountrySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MccCodeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RegionCurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端基础数据异步导出提交接口，保留原同步路径供兼容调用。
 */
@RestController
@RequestMapping("/admin/base")
public class AdminBaseExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建基础数据异步导出提交接口。
     *
     * @param applicationService 统一异步导出编排服务
     */
    public AdminBaseExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结卡 BIN 查询条件和当前操作人上下文并创建后台导出任务。
     *
     * @param request 浏览器提交的幂等键和查询条件
     * @return 已创建或幂等命中的导出任务摘要
     */
    @PostMapping("/card-bin/export-task")
    @RequiresPermission("base:cardBin:export")
    @OperationLog(moduleName = "卡BIN库管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建卡BIN库导出任务")
    public CommonResult<TaskSummary> submitCardBins(@Valid @RequestBody CardBinSubmitRequest request) {
        return success(applicationService.submitCardBins(request));
    }

    /**
     * 冻结国家和地区筛选条件并创建后台导出任务。
     *
     * @param request 浏览器提交的幂等键和筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    @PostMapping("/countries/export-task")
    @RequiresPermission("base:country:export")
    @OperationLog(moduleName = "国家地区", businessType = OperationTypeConstants.EXPORT,
            operation = "创建国家地区导出任务")
    public CommonResult<TaskSummary> submitCountries(@Valid @RequestBody CountrySubmitRequest request) {
        return success(applicationService.submitCountries(request));
    }

    /**
     * 冻结币种筛选条件并创建后台导出任务。
     *
     * @param request 浏览器提交的幂等键和筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    @PostMapping("/currencies/export-task")
    @RequiresPermission("base:currency:export")
    @OperationLog(moduleName = "币种管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建币种导出任务")
    public CommonResult<TaskSummary> submitCurrencies(@Valid @RequestBody CurrencySubmitRequest request) {
        return success(applicationService.submitCurrencies(request));
    }

    /**
     * 冻结国家默认币种关系筛选条件并创建后台导出任务。
     *
     * @param request 浏览器提交的幂等键和筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    @PostMapping("/region-currencies/export-task")
    @RequiresPermission("base:countryCurrency:export")
    @OperationLog(moduleName = "地区币种配置", businessType = OperationTypeConstants.EXPORT,
            operation = "创建地区币种配置导出任务")
    public CommonResult<TaskSummary> submitRegionCurrencies(
            @Valid @RequestBody RegionCurrencySubmitRequest request) {
        return success(applicationService.submitRegionCurrencies(request));
    }

    /**
     * 冻结 MCC 树筛选条件并创建编码后台导出任务。
     *
     * @param request 浏览器提交的幂等键和筛选条件
     * @return 已创建或幂等命中的任务摘要
     */
    @PostMapping("/mcc/code/export-task")
    @RequiresPermission("base:mcc:tree:export")
    @OperationLog(moduleName = "MCC 管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建 MCC 编码导出任务")
    public CommonResult<TaskSummary> submitMccCodes(@Valid @RequestBody MccCodeSubmitRequest request) {
        return success(applicationService.submitMccCodes(request));
    }
}
