package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.monitor.AdminMonitorDatasourceApplicationService;
import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.export.DataSourceMonitorExportRow;
import com.scott.payment.admin.dto.exporttask.AdminDatasourceExportInternalDTOs;
import com.scott.payment.admin.dto.exporttask.AdminRiskExportInternalDTOs;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 风控配置与数据源监控异步导出的内部来源编排，不暴露浏览器会话或基础设施秘密。
 */
@Service
public class AdminRiskMonitorExportSourceApplicationService {
    private final AdminRiskManagementApplicationService riskApplicationService;
    private final AdminMonitorDatasourceApplicationService datasourceApplicationService;

    /** 创建本批次的内部导出来源服务。 */
    public AdminRiskMonitorExportSourceApplicationService(
            AdminRiskManagementApplicationService riskApplicationService,
            AdminMonitorDatasourceApplicationService datasourceApplicationService) {
        this.riskApplicationService = riskApplicationService;
        this.datasourceApplicationService = datasourceApplicationService;
    }

    /** 按受控功能定义读取一页风控配置动态安全投影。 */
    public AdminRiskExportInternalDTOs.PageResponse pageRiskConfig(
            AdminRiskExportInternalDTOs.PageRequest request) {
        return riskApplicationService.pageExport(request);
    }

    /**
     * 从当前服务实时快照读取一页脱敏数据源投影。
     */
    public AdminDatasourceExportInternalDTOs.PageResponse pageDatasourceSnapshot(
            AdminDatasourceExportInternalDTOs.PageRequest request) {
        if (request == null || request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageSize() == null || request.getPageSize() < 1
                || request.getPageSize() > 1000) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        Locale locale = request.getLocale() == null
                ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(request.getLocale());
        List<DataSourceMonitorExportRow> rows = datasourceApplicationService.exportRows(locale);
        int fromIndex = Math.min((request.getPageNo() - 1) * request.getPageSize(), rows.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), rows.size());
        AdminDatasourceExportInternalDTOs.PageResponse response =
                new AdminDatasourceExportInternalDTOs.PageResponse();
        response.setTotal(rows.size());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        response.setRecords(rows.subList(fromIndex, toIndex));
        return response;
    }
}
