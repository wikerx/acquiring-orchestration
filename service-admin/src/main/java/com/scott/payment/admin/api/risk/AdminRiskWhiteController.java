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
 * @classname : AdminRiskWhiteController
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 白名单管理接口，位于 service-admin 接口层，仅维护管理端白名单配置。
 * @status : create
 */
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskWhiteController {

    private static final String MODULE_TYPE = "WHITE";

    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    public AdminRiskWhiteController(AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 分页查询白名单。
     *
     * @param functionCode 功能编码
     * @param request      查询条件
     * @return 白名单分页结果
     */
    @PostMapping("/list/WHITE/{functionCode}/page")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.QUERY, operation = "分页查询白名单")
    public CommonResult<PageResult<RiskDTOs.RiskRecordResponse>> page(@PathVariable("functionCode") String functionCode,
                                                                      @RequestBody(required = false) RiskDTOs.RiskListQueryRequest request) {
        return success(riskManagementApplicationService.pageList(MODULE_TYPE, functionCode, request));
    }

    /**
     * 查询白名单详情。
     *
     * @param functionCode 功能编码
     * @param id           记录ID
     * @return 白名单详情
     */
    @GetMapping("/list/WHITE/{functionCode}/{id}")
    @RequiresPermission("risk:whitelist:list")
    public CommonResult<RiskDTOs.RiskRecordResponse> detail(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id) {
        return success(riskManagementApplicationService.listDetail(MODULE_TYPE, functionCode, id));
    }

    /**
     * 查询白名单编辑详情。
     *
     * @param functionCode 功能编码
     * @param id           记录ID
     * @return 白名单编辑详情，敏感值仅在编辑权限下回显
     */
    @GetMapping("/list/WHITE/{functionCode}/{id}/edit")
    @RequiresPermission("risk:whitelist:list")
    public CommonResult<RiskDTOs.RiskRecordResponse> editDetail(@PathVariable("functionCode") String functionCode,
                                                               @PathVariable("id") Long id) {
        return success(riskManagementApplicationService.listEditDetail(MODULE_TYPE, functionCode, id));
    }

    /**
     * 新增白名单。
     *
     * @param functionCode 功能编码
     * @param request      保存请求
     * @return 新增后的白名单记录
     */
    @PostMapping("/list/WHITE/{functionCode}")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.CREATE, operation = "新增白名单")
    public CommonResult<RiskDTOs.RiskRecordResponse> create(@PathVariable("functionCode") String functionCode,
                                                           @Valid @RequestBody RiskDTOs.RiskListSaveRequest request) {
        return success(riskManagementApplicationService.createList(MODULE_TYPE, functionCode, request));
    }

    /**
     * 修改白名单。
     *
     * @param functionCode 功能编码
     * @param id           记录ID
     * @param request      保存请求
     * @return 修改后的白名单记录
     */
    @PutMapping("/list/WHITE/{functionCode}/{id}")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.UPDATE, operation = "修改白名单")
    public CommonResult<RiskDTOs.RiskRecordResponse> update(@PathVariable("functionCode") String functionCode,
                                                           @PathVariable("id") Long id,
                                                           @Valid @RequestBody RiskDTOs.RiskListSaveRequest request) {
        return success(riskManagementApplicationService.updateList(MODULE_TYPE, functionCode, id, request));
    }

    /**
     * 删除白名单。
     *
     * @param functionCode 功能编码
     * @param id           记录ID
     * @return 空结果
     */
    @DeleteMapping("/WHITE/{functionCode}/{id}")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.DELETE, operation = "删除白名单")
    public CommonResult<Void> remove(@PathVariable("functionCode") String functionCode,
                                     @PathVariable("id") Long id) {
        riskManagementApplicationService.remove(MODULE_TYPE, functionCode, id);
        return success();
    }

    /**
     * 批量删除白名单。
     *
     * @param functionCode 功能编码
     * @param request      批量删除请求
     * @return 空结果
     */
    @DeleteMapping("/WHITE/{functionCode}/batch")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.DELETE, operation = "批量删除白名单")
    public CommonResult<Void> batchRemove(@PathVariable("functionCode") String functionCode,
                                          @RequestBody RiskDTOs.BatchRemoveRequest request) {
        riskManagementApplicationService.batchRemove(MODULE_TYPE, functionCode, request);
        return success();
    }

    /**
     * 更新白名单状态。
     *
     * @param functionCode 功能编码
     * @param id           记录ID
     * @param request      状态更新请求
     * @return 更新后的白名单记录
     */
    @PutMapping("/WHITE/{functionCode}/{id}/status")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.UPDATE, operation = "更新白名单状态")
    public CommonResult<RiskDTOs.RiskRecordResponse> updateStatus(@PathVariable("functionCode") String functionCode,
                                                                 @PathVariable("id") Long id,
                                                                 @RequestBody RiskDTOs.StatusUpdateRequest request) {
        return success(riskManagementApplicationService.updateStatus(MODULE_TYPE, functionCode, id, request));
    }

    /**
     * 导出白名单。
     *
     * @param functionCode 功能编码
     * @param response     HTTP 响应
     */
    @PostMapping("/WHITE/{functionCode}/export")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.EXPORT, operation = "导出白名单")
    public void export(@PathVariable("functionCode") String functionCode, HttpServletResponse response) {
        riskManagementApplicationService.export(MODULE_TYPE, functionCode, response);
    }

    /**
     * 下载白名单导入模板。
     *
     * @param functionCode 功能编码
     * @param response     HTTP 响应
     */
    @GetMapping("/WHITE/{functionCode}/template")
    @RequiresPermission("risk:whitelist:list")
    public void template(@PathVariable("functionCode") String functionCode, HttpServletResponse response) {
        riskManagementApplicationService.template(MODULE_TYPE, functionCode, response);
    }

    /**
     * 导入白名单 CSV 文件。
     *
     * @param functionCode 功能编码
     * @param file         CSV 文件
     * @return 导入结果
     */
    @PostMapping("/WHITE/{functionCode}/import")
    @RequiresPermission("risk:whitelist:list")
    @OperationLog(moduleName = "收单风控-白名单", businessType = OperationTypeConstants.CREATE, operation = "导入白名单")
    public CommonResult<RiskDTOs.ImportResultResponse> importCsv(@PathVariable("functionCode") String functionCode,
                                                                 @RequestParam("file") MultipartFile file) {
        return success(riskManagementApplicationService.importCsv(MODULE_TYPE, functionCode, file));
    }
}
