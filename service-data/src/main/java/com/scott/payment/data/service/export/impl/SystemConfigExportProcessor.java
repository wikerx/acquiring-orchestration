package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SystemConfigQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.SystemConfigExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SYSTEM_CONFIG;

/**
 * 管理端系统参数异步导出处理器。
 *
 * <p>参数原值的敏感性只能由 Admin 在权威数据边界判断；Data 仅消费已经脱敏的安全投影，
 * 不持有隐藏、加密或密钥类系统参数原值。</p>
 */
@Service
public class SystemConfigExportProcessor
        extends AbstractBaseReferenceExportProcessor<SystemConfigQuery, SystemConfigExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建系统参数异步导出处理器。
     *
     * @param sourceClient    Admin 权威导出数据源客户端
     * @param artifactWriter  Excel 文件写入器
     * @param messageResolver Excel 国际化文案解析器
     * @param properties      异步导出分页与文件配置
     */
    public SystemConfigExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                       ExcelExportArtifactWriter artifactWriter,
                                       ExcelI18nMessageResolver messageResolver,
                                       DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回处理器唯一支持的系统参数任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_SYSTEM_CONFIG;
    }

    /**
     * 返回冻结查询快照的强类型模型。
     */
    @Override
    protected Class<SystemConfigQuery> queryClass() {
        return SystemConfigQuery.class;
    }

    /**
     * 返回系统参数工作簿标题的国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.config.title";
    }

    /**
     * 返回缺少中文资源时使用的安全标题。
     */
    @Override
    protected String chineseTitle() {
        return "系统参数";
    }

    /**
     * 返回缺少英文资源时使用的安全标题。
     */
    @Override
    protected String englishTitle() {
        return "System Configuration";
    }

    /**
     * 返回系统参数 Excel 行模型。
     */
    @Override
    protected Class<SystemConfigExportRow> rowClass() {
        return SystemConfigExportRow.class;
    }

    /**
     * 从 Admin 分页读取已脱敏系统参数，并转换为本地化 Excel 行。
     *
     * @param task   当前持久化导出任务
     * @param query  任务创建时冻结的筛选条件
     * @param pageNo 当前导出页码
     * @param locale 任务创建时冻结的语言环境
     * @return 当前页行数据和相同条件下的总行数
     */
    @Override
    protected ExportPage<SystemConfigExportRow> loadPage(
            DataExportTaskDO task, SystemConfigQuery query, int pageNo, Locale locale) {
        SystemConfigPageRequest request = new SystemConfigPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(toSourceQuery(query));
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        SystemConfigPageResponse response = sourceClient.loadSystemConfigPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SystemConfigExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 在服务契约边界逐字段复制查询，禁止把 Data 分页参数或动态字段透传给 Admin。
     *
     * @param source Data 任务中的冻结查询
     * @return Admin 权威数据源查询
     */
    private com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigQuery
    toSourceQuery(SystemConfigQuery source) {
        var target = new com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemConfigQuery();
        target.setConfigName(source.getConfigName());
        target.setConfigKey(source.getConfigKey());
        target.setConfigGroup(source.getConfigGroup());
        target.setStatus(source.getStatus());
        return target;
    }

    /**
     * 将 Admin 安全投影转换为 Excel 行，保持脱敏标记原样且不尝试恢复参数原值。
     *
     * @param source Admin 返回的已脱敏系统参数投影
     * @param locale 任务语言环境
     * @return 可写入 Excel 的系统参数行
     */
    private SystemConfigExportRow toExportRow(SystemConfigRow source, Locale locale) {
        SystemConfigExportRow row = new SystemConfigExportRow();
        row.setConfigName(source.getConfigName());
        row.setConfigKey(source.getConfigKey());
        row.setConfigGroup(source.getConfigGroup());
        row.setConfigValue(source.getConfigValue());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
