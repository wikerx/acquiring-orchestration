package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DictTypeQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.DictTypeExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_DICT_TYPE;

/**
 * 管理端字典类型异步导出处理器，按冻结筛选条件读取 Admin 安全投影。
 */
@Service
public class DictTypeExportProcessor
        extends AbstractBaseReferenceExportProcessor<DictTypeQuery, DictTypeExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建字典类型异步导出处理器并注入权威数据源与统一文件写入能力。
     */
    public DictTypeExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                   ExcelExportArtifactWriter artifactWriter,
                                   ExcelI18nMessageResolver messageResolver,
                                   DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回字典类型导出的稳定任务类型，供任务分发器选择当前处理器。
     */
    @Override
    public String taskType() {
        return ADMIN_DICT_TYPE;
    }

    /**
     * 返回字典类型查询快照类型，确保筛选条件按强类型反序列化。
     */
    @Override
    protected Class<DictTypeQuery> queryClass() {
        return DictTypeQuery.class;
    }

    /**
     * 返回字典类型 Excel 标题的国际化资源键。
     */
    @Override
    protected String titleKey() {
        return "excel.dict.title";
    }

    /**
     * 返回字典类型文件名的中文兜底标题。
     */
    @Override
    protected String chineseTitle() {
        return "字典类型";
    }

    /**
     * 返回字典类型文件名的英文兜底标题。
     */
    @Override
    protected String englishTitle() {
        return "Dictionary Types";
    }

    /**
     * 返回字典类型专用 Excel 行模型，保持类型编码的文本语义。
     */
    @Override
    protected Class<DictTypeExportRow> rowClass() {
        return DictTypeExportRow.class;
    }

    /**
     * 按冻结筛选条件分页读取字典类型安全投影，并转换为本地化 Excel 行。
     */
    @Override
    protected ExportPage<DictTypeExportRow> loadPage(
            DataExportTaskDO task, DictTypeQuery query, int pageNo, Locale locale) {
        DictTypePageRequest request = new DictTypePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        DictTypePageResponse response = sourceClient.loadDictTypePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<DictTypeExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将字典类型安全投影转换为 Excel 行，并本地化状态及系统内置标记。
     */
    private DictTypeExportRow toExportRow(DictTypeRow source, Locale locale) {
        DictTypeExportRow row = new DictTypeExportRow();
        row.setDictName(source.getDictName());
        row.setDictType(source.getDictType());
        row.setBizDomain(source.getBizDomain());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setSystemBuiltin(booleanLabel(source.getSystemBuiltin(), locale));
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
