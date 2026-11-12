package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DepartmentQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.DepartmentExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_DEPARTMENT;

/**
 * 管理端部门异步导出处理器，按页读取 Admin 安全投影并本地化状态。
 */
@Service
public class DepartmentExportProcessor
        extends AbstractBaseReferenceExportProcessor<DepartmentQuery, DepartmentExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建部门异步导出处理器并注入权威数据源与统一文件写入能力。
     */
    public DepartmentExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                     ExcelExportArtifactWriter artifactWriter,
                                     ExcelI18nMessageResolver messageResolver,
                                     DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回部门导出的稳定任务类型，供任务分发器选择当前处理器。
     */
    @Override
    public String taskType() {
        return ADMIN_DEPARTMENT;
    }

    /**
     * 返回部门查询快照类型，确保任务快照按强类型反序列化。
     */
    @Override
    protected Class<DepartmentQuery> queryClass() {
        return DepartmentQuery.class;
    }

    /**
     * 返回部门 Excel 标题的国际化资源键。
     */
    @Override
    protected String titleKey() {
        return "excel.dept.title";
    }

    /**
     * 返回部门文件名的中文兜底标题。
     */
    @Override
    protected String chineseTitle() {
        return "部门";
    }

    /**
     * 返回部门文件名的英文兜底标题。
     */
    @Override
    protected String englishTitle() {
        return "Departments";
    }

    /**
     * 返回部门专用 Excel 行模型，避免数据库对象直接进入文件层。
     */
    @Override
    protected Class<DepartmentExportRow> rowClass() {
        return DepartmentExportRow.class;
    }

    /**
     * 按平台分页容量读取部门安全投影，并转换为当前任务语言的 Excel 行。
     */
    @Override
    protected ExportPage<DepartmentExportRow> loadPage(
            DataExportTaskDO task, DepartmentQuery query, int pageNo, Locale locale) {
        DepartmentPageRequest request = new DepartmentPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        DepartmentPageResponse response = sourceClient.loadDepartmentPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<DepartmentExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将部门安全投影转换为 Excel 行，并本地化启停状态。
     */
    private DepartmentExportRow toExportRow(DepartmentRow source, Locale locale) {
        DepartmentExportRow row = new DepartmentExportRow();
        row.setDeptName(source.getDeptName());
        row.setParentName(source.getParentName());
        row.setSortNo(source.getSortNo());
        row.setLeader(source.getLeader());
        row.setPhone(source.getPhone());
        row.setEmail(source.getEmail());
        row.setStatus(statusLabel(source.getStatus(), locale));
        return row;
    }
}
