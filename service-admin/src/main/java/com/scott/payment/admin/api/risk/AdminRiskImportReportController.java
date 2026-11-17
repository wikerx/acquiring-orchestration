package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.risk.AdminRiskImportReportApplicationService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;

/**
 * 导入失败文件下载入口；应用服务继续校验原创建账号和具体风控功能权限。
 */
@RestController
@RequestMapping("/admin/risk/imports")
public class AdminRiskImportReportController {
    private final AdminRiskImportReportApplicationService service;

    /**
     * 注入失败报告用例。
     */
    public AdminRiskImportReportController(AdminRiskImportReportApplicationService service) {
        this.service = service;
    }

    /**
     * 下载当前账号失败批次的完整脱敏明细，不包含原始匹配值。
     */
    @GetMapping("/{batchNo}/failures")
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控-导入反馈", businessType = OperationTypeConstants.EXPORT, operation = "下载风控导入失败明细")
    public void failures(@PathVariable("batchNo") String batchNo, HttpServletResponse response) throws IOException {
        service.download(batchNo, response);
    }
}
