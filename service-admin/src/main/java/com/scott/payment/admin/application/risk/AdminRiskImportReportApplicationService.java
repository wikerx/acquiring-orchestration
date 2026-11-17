package com.scott.payment.admin.application.risk;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 普通风控导入错误报告，仅向原导入账号返回行号与脱敏原因，复用对应功能的导入权限。
 */
@Service
public class AdminRiskImportReportApplicationService {
    private final RiskManagementMapper mapper;

    /**
     * 创建失败报告应用服务，读取独立事务已提交的错误日志。
     */
    public AdminRiskImportReportApplicationService(RiskManagementMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 使用稳定账号 ID 绑定新导入批次；历史显示名称不作为下载授权依据。
     */
    static String currentOwner() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || account.getAccountId() <= 0) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return "account:" + account.getAccountId();
    }

    /**
     * 在响应开始前校验账号、批次归属、终态与功能权限；完整报告按游标流式写出。
     */
    @DS(DataSourceName.MASTER)
    public void download(String batchNo, HttpServletResponse response) throws IOException {
        String owner = currentOwner();
        if (batchNo == null || !batchNo.matches("RISK-[A-Za-z0-9-]{1,64}")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        Map<String, Object> batch = mapper.selectImportFailureBatch(batchNo);
        if (batch == null || !owner.equals(batch.get("operator"))) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(
                String.valueOf(batch.get("module_type")), String.valueOf(batch.get("function_code")));
        List<String> permissions = InternalAuthContextHolder.get().getPermissions();
        if (permissions == null || (!permissions.contains("*:*:*")
                && !permissions.contains(definition.getPermissionPrefix() + ":import"))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        if (!(batch.get("status") instanceof Number status) || status.intValue() != 3) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + batchNo + "-failures.csv\"");
        response.setHeader("Cache-Control", "no-store");
        var writer = response.getWriter();
        writer.write("\uFEFFrowNo,errorMessage\r\n");
        int afterRow = 0;
        long afterId = 0;
        while (true) {
            List<Map<String, Object>> rows = mapper.selectImportFailureRows(batchNo, afterRow, afterId);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                int rowNo = ((Number) row.get("row_no")).intValue();
                long id = ((Number) row.get("id")).longValue();
                // 固定前缀同时阻止 CSV 公式注入；永不输出 raw_content。
                String reason = String.valueOf(row.get("error_message"))
                        .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "***")
                        .replaceAll("\\d{12,19}", "***");
                writer.write(rowNo + ",\"line " + rowNo + ": " + reason.replace("\"", "\"\"") + "\"\r\n");
                afterRow = rowNo;
                afterId = id;
            }
            if (rows.size() < 500) break;
        }
        writer.flush();
    }
}
