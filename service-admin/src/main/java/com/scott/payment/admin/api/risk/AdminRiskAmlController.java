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
 * @classname : AdminRiskAmlController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : AML 强制拦截名单管理接口，位于 service-admin 接口层，仅维护管理端 AML 配置。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskAmlController {

    private static final String MODULE_TYPE = "AML";

    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    /**
     * 创建 AML 强制拦截名单接口。
     *
     * @param riskManagementApplicationService 风控管理应用服务
     */
    public AdminRiskAmlController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 分页查询 AML 名单。
     *
     * @param functionCode 功能编码
     * @param request      查询条件
     * @return AML 名单分页结果
     */
    @PostMapping("/list/AML/{functionCode}/page")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.QUERY, operation = "分页查询AML名单")
    public CommonResult<PageResult<RiskDTOs.RiskRecordResponse>> page(@PathVariable("functionCode") String functionCode,
                                                                      @RequestBody(required = false) RiskDTOs.RiskListQueryRequest request) {
        return success(riskManagementApplicationService.pageList(MODULE_TYPE, functionCode, request));
    }

    /**
     * 查询 AML 名单详情。
     *
     * @param functionCode 功能编码
     * @param id           名单记录ID
     * @return AML 名单详情
     */
    @GetMapping("/list/AML/{functionCode}/{id}")
    @RequiresPermission("risk:access")
    public CommonResult<RiskDTOs.RiskRecordResponse> detail(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id) {
        return success(riskManagementApplicationService.listDetail(MODULE_TYPE, functionCode, id));
    }

    /**
     * 查询 AML 名单编辑详情。
     *
     * @param functionCode 功能编码
     * @param id           名单记录ID
     * @return AML 编辑详情，敏感值仅在编辑权限下回显
     */
    @GetMapping("/list/AML/{functionCode}/{id}/edit")
    @RequiresPermission("risk:access")
    public CommonResult<RiskDTOs.RiskRecordResponse> editDetail(@PathVariable("functionCode") String functionCode,
                                                               @PathVariable("id") Long id) {
        return success(riskManagementApplicationService.listEditDetail(MODULE_TYPE, functionCode, id));
    }

    /**
     * 新增 AML 名单。
     *
     * @param functionCode 功能编码
     * @param request      保存请求
     * @return 新增后的名单记录
     */
    @PostMapping("/list/AML/{functionCode}")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.CREATE, operation = "新增AML名单")
    public CommonResult<RiskDTOs.RiskRecordResponse> create(@PathVariable("functionCode") String functionCode,
                                                           @Valid @RequestBody RiskDTOs.RiskListSaveRequest request) {
        return success(riskManagementApplicationService.createList(MODULE_TYPE, functionCode, request));
    }

    /**
     * 修改 AML 名单。
     *
     * @param functionCode 功能编码
     * @param id           名单记录ID
     * @param request      保存请求
     * @return 修改后的名单记录
     */
    @PutMapping("/list/AML/{functionCode}/{id}")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.UPDATE, operation = "修改AML名单")
    public CommonResult<RiskDTOs.RiskRecordResponse> update(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id,
                                                           @Valid @RequestBody RiskDTOs.RiskListSaveRequest request) {
        return success(riskManagementApplicationService.updateList(MODULE_TYPE, functionCode, id, request));
    }

    /**
     * 删除 AML 名单。
     *
     * @param functionCode 功能编码
     * @param id           名单记录ID
     * @return 空结果
     */
    @DeleteMapping("/AML/{functionCode}/{id}")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.DELETE, operation = "删除AML名单")
    public CommonResult<Void> remove(@PathVariable("functionCode") String functionCode,
                                     @PathVariable("id") Long id) {
        riskManagementApplicationService.remove(MODULE_TYPE, functionCode, id);
        return success();
    }

    /**
     * 批量删除 AML 名单。
     *
     * @param functionCode 功能编码
     * @param request      批量删除请求
     * @return 空结果
     */
    @DeleteMapping("/AML/{functionCode}/batch")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.DELETE, operation = "批量删除AML名单")
    public CommonResult<Void> batchRemove(@PathVariable("functionCode") String functionCode,
                                          @RequestBody RiskDTOs.BatchRemoveRequest request) {
        riskManagementApplicationService.batchRemove(MODULE_TYPE, functionCode, request);
        return success();
    }

    /**
     * 更新 AML 名单状态。
     *
     * @param functionCode 功能编码
     * @param id           名单记录ID
     * @param request      状态更新请求
     * @return 更新后的名单记录
     */
    @PutMapping("/AML/{functionCode}/{id}/status")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.UPDATE, operation = "更新AML名单状态")
    public CommonResult<RiskDTOs.RiskRecordResponse> updateStatus(@PathVariable("functionCode") String functionCode,
                                                                 @PathVariable("id") Long id,
                                                                 @RequestBody RiskDTOs.StatusUpdateRequest request) {
        return success(riskManagementApplicationService.updateStatus(MODULE_TYPE, functionCode, id, request));
    }

    /**
     * 导出 AML 名单。
     *
     * @param functionCode 功能编码
     * @param request      导出筛选条件
     * @param response     HTTP 响应
     */
    @PostMapping("/AML/{functionCode}/export")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.EXPORT, operation = "导出AML名单")
    public void export(@PathVariable("functionCode") String functionCode,
                       @RequestBody(required = false) RiskDTOs.RiskListQueryRequest request,
                       HttpServletResponse response) {
        riskManagementApplicationService.export(MODULE_TYPE, functionCode, request, response);
    }

    /**
     * 下载 AML 名单导入模板。
     *
     * @param functionCode 功能编码
     * @param response     HTTP 响应
     */
    @GetMapping("/AML/{functionCode}/template")
    @RequiresPermission("risk:access")
    public void template(@PathVariable("functionCode") String functionCode, HttpServletResponse response) {
        riskManagementApplicationService.template(MODULE_TYPE, functionCode, response);
    }

    /**
     * 导入 AML 名单配置文件。
     *
     * @param functionCode 功能编码
     * @param file         CSV 或 Excel 文件
     * @return 导入结果
     */
    @PostMapping("/AML/{functionCode}/import")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-AML", businessType = OperationTypeConstants.CREATE, operation = "导入AML名单")
    public CommonResult<RiskDTOs.ImportResultResponse> importCsv(@PathVariable("functionCode") String functionCode,
                                                                 @RequestParam("file") MultipartFile file) {
        return success(riskManagementApplicationService.importCsv(MODULE_TYPE, functionCode, file));
    }
}
