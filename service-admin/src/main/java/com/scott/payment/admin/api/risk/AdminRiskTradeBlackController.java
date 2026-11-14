package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.application.risk.AdminTradeBlackWorkbookApplicationService;
import com.scott.payment.admin.application.risk.AdminTradeBlackWorkflowApplicationService;
import com.scott.payment.admin.application.system.LegacyFileTemplateBridge;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.dto.risk.TradeBlackWorkflowDTOs;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs;
import com.scott.payment.admin.support.risk.TradeBlackUploadParser;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.template.LegacyFileTemplateAdapter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 系统交易加黑接口。交易事实、脱敏值和哈希全部由服务端解析，客户端不能直接提交名单值。
 */
@RestController
@RequestMapping("/admin/risk/trade-black")
public class AdminRiskTradeBlackController {

    private final AdminRiskManagementApplicationService riskManagementApplicationService;
    private final AdminTradeBlackWorkflowApplicationService workflowApplicationService;
    private final AdminTradeBlackWorkbookApplicationService workbookApplicationService;
    private final AdminExportTaskApplicationService exportTaskApplicationService;
    private final TradeBlackUploadParser uploadParser;
    private final LegacyFileTemplateBridge fileTemplateBridge;

    /**
     * 创建系统交易加黑接口。
     *
     * @param riskManagementApplicationService 风控管理应用服务
     * @param workflowApplicationService 系统交易加黑工作流服务
     * @param workbookApplicationService 系统交易加黑 Excel 服务
     * @param exportTaskApplicationService 异步导出任务应用服务
     * @param uploadParser 交易定位文件解析器
     */
    public AdminRiskTradeBlackController(AdminRiskManagementApplicationService riskManagementApplicationService,
                                         AdminTradeBlackWorkflowApplicationService workflowApplicationService,
                                         AdminTradeBlackWorkbookApplicationService workbookApplicationService,
                                         AdminExportTaskApplicationService exportTaskApplicationService,
                                         TradeBlackUploadParser uploadParser,
                                         LegacyFileTemplateBridge fileTemplateBridge) {
        this.riskManagementApplicationService = riskManagementApplicationService;
        this.workflowApplicationService = workflowApplicationService;
        this.workbookApplicationService = workbookApplicationService;
        this.exportTaskApplicationService = exportTaskApplicationService;
        this.uploadParser = uploadParser;
        this.fileTemplateBridge = fileTemplateBridge;
    }

    /**
     * 分页查询系统交易加黑记录。
     *
     * @param request 查询条件
     * @return 交易加黑分页结果
     */
    @PostMapping("/page")
    @RequiresPermission("risk:tradeBlack:system:list")
    public CommonResult<PageResult<RiskDTOs.TradeBlackResponse>> page(
            @RequestBody(required = false) RiskDTOs.TradeBlackQueryRequest request) {
        return success(riskManagementApplicationService.pageTradeBlack(request));
    }

    /** 冻结当前筛选条件并创建系统交易加黑异步导出任务。 */
    @PostMapping("/export-tasks")
    @RequiresPermission("risk:tradeBlack:system:export")
    @OperationLog(moduleName = "收单风控-系统交易加黑", businessType = OperationTypeConstants.EXPORT,
            operation = "创建系统交易加黑导出任务")
    public CommonResult<AdminExportTaskDTOs.TaskSummary> submitExport(
            @Valid @RequestBody AdminExportTaskDTOs.TradeBlackSubmitRequest request) {
        return success(exportTaskApplicationService.submitTradeBlack(request));
    }

    /** 生成服务端脱敏候选项；同时校验系统交易权限和每个实际名单功能权限。 */
    @PostMapping("/preview")
    @RequiresPermission("risk:tradeBlack:system:list")
    public CommonResult<TradeBlackWorkflowDTOs.PreviewResponse> preview(
            @Valid @RequestBody TradeBlackWorkflowDTOs.PreviewRequest request) {
        return success(workflowApplicationService.preview(request));
    }

    /** 上传 XLSX、XLS 或 CSV 交易定位文件并生成服务端脱敏候选项。 */
    @PostMapping(value = "/preview-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("risk:tradeBlack:system:batchAdd")
    public CommonResult<TradeBlackWorkflowDTOs.PreviewResponse> previewFile(
            @RequestPart("metadata") String metadata,
            @RequestPart("file") MultipartFile file) {
        return success(workflowApplicationService.preview(uploadParser.parse(metadata, file)));
    }

    /** 执行选中的候选项，实际写入或解除对应真实黑名单。 */
    @PostMapping("/execute")
    @RequiresPermission("risk:tradeBlack:system:list")
    @OperationLog(moduleName = "收单风控-系统交易加黑", businessType = OperationTypeConstants.CREATE, operation = "执行系统交易加黑工作流")
    public CommonResult<TradeBlackWorkflowDTOs.ExecuteResponse> execute(
            @Valid @RequestBody TradeBlackWorkflowDTOs.ExecuteRequest request) {
        return success(workflowApplicationService.execute(request));
    }

    /** 文件模式执行；服务端重新解析同一文件，浏览器仍不提交任何名单值。 */
    @PostMapping(value = "/execute-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("risk:tradeBlack:system:batchAdd")
    @OperationLog(moduleName = "收单风控-系统交易加黑", businessType = OperationTypeConstants.CREATE, operation = "执行系统交易批量加黑工作流")
    public CommonResult<TradeBlackWorkflowDTOs.ExecuteResponse> executeFile(
            @RequestPart("requestId") String requestId,
            @RequestPart("metadata") String metadata,
            @RequestPart("selectedCandidateKeys") String selectedCandidateKeys,
            @RequestPart("file") MultipartFile file) {
        TradeBlackWorkflowDTOs.ExecuteRequest request = new TradeBlackWorkflowDTOs.ExecuteRequest();
        request.setRequestId(requestId);
        request.setWorkflow(uploadParser.parse(metadata, file));
        request.setSelectedCandidateKeys(JsonUtils.parseArray(selectedCandidateKeys, String.class));
        return success(workflowApplicationService.execute(request));
    }

    /** 下载只包含交易定位字段的国际化 Excel 模板，不包含敏感值、脱敏值或哈希列。 */
    @GetMapping("/template")
    @RequiresPermission("risk:tradeBlack:system:batchAdd")
    @LegacyFileTemplateAdapter(value = "RISK_TRADE_BLACK_IMPORT", removeAfter = "2026-12-31")
    public void template(HttpServletResponse response) {
        if (fileTemplateBridge.writeIfPublished("RISK_TRADE_BLACK_IMPORT", response)) {
            return;
        }
        workbookApplicationService.downloadTemplate(response);
    }

    /** 下载当前操作人指定批次的国际化 Excel 失败明细。 */
    @GetMapping("/batches/{batchNo}/failures")
    @RequiresPermission("risk:tradeBlack:system:list")
    public void failureReport(@PathVariable String batchNo,
                              HttpServletResponse response) {
        workbookApplicationService.downloadFailureReport(batchNo, response);
    }
}
