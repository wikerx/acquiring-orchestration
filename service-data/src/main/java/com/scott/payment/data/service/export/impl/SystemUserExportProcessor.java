package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SystemUserQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.SystemUserExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SYSTEM_USER;

/**
 * 管理端系统账户异步导出处理器。
 *
 * <p>处理器只消费任务创建时冻结的查询条件，并通过 Admin 权威分页接口读取安全投影，
 * 避免 Data 直接访问管理域数据表或重新解释账户权限。</p>
 */
@Service
public class SystemUserExportProcessor
        extends AbstractBaseReferenceExportProcessor<SystemUserQuery, SystemUserExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建系统账户异步导出处理器。
     *
     * @param sourceClient    Admin 权威导出数据源客户端
     * @param artifactWriter  Excel 文件写入器
     * @param messageResolver Excel 国际化文案解析器
     * @param properties      异步导出分页与文件配置
     */
    public SystemUserExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                     ExcelExportArtifactWriter artifactWriter,
                                     ExcelI18nMessageResolver messageResolver,
                                     DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回处理器唯一支持的系统账户任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_SYSTEM_USER;
    }

    /**
     * 返回冻结查询快照的强类型模型。
     */
    @Override
    protected Class<SystemUserQuery> queryClass() {
        return SystemUserQuery.class;
    }

    /**
     * 返回系统账户工作簿标题的国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.user.title";
    }

    /**
     * 返回缺少中文资源时使用的安全标题。
     */
    @Override
    protected String chineseTitle() {
        return "系统账户";
    }

    /**
     * 返回缺少英文资源时使用的安全标题。
     */
    @Override
    protected String englishTitle() {
        return "System Users";
    }

    /**
     * 返回系统账户 Excel 行模型。
     */
    @Override
    protected Class<SystemUserExportRow> rowClass() {
        return SystemUserExportRow.class;
    }

    /**
     * 从 Admin 分页读取系统账户安全投影，并转换为本地化 Excel 行。
     *
     * @param task   当前持久化导出任务
     * @param query  任务创建时冻结的筛选条件
     * @param pageNo 当前导出页码
     * @param locale 任务创建时冻结的语言环境
     * @return 当前页行数据和相同条件下的总行数
     */
    @Override
    protected ExportPage<SystemUserExportRow> loadPage(
            DataExportTaskDO task, SystemUserQuery query, int pageNo, Locale locale) {
        SystemUserPageRequest request = new SystemUserPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(toSourceQuery(query));
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        SystemUserPageResponse response = sourceClient.loadSystemUserPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SystemUserExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 在服务契约边界逐字段复制查询，禁止把 Data 分页参数或动态字段透传给 Admin。
     *
     * @param source Data 任务中的冻结查询
     * @return Admin 权威数据源查询
     */
    private com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserQuery
    toSourceQuery(SystemUserQuery source) {
        var target = new com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SystemUserQuery();
        target.setLoginAccount(source.getLoginAccount());
        target.setMobile(source.getMobile());
        target.setEmail(source.getEmail());
        target.setDeptId(source.getDeptId());
        target.setStatus(source.getStatus());
        target.setEmailVerificationStatus(source.getEmailVerificationStatus());
        target.setActivationStatus(source.getActivationStatus());
        return target;
    }

    /**
     * 将 Admin 安全投影转换为 Excel 行，并按任务语言本地化状态和集合分隔符。
     *
     * @param source Admin 返回的系统账户安全投影
     * @param locale 任务语言环境
     * @return 可写入 Excel 的系统账户行
     */
    private SystemUserExportRow toExportRow(SystemUserRow source, Locale locale) {
        SystemUserExportRow row = new SystemUserExportRow();
        row.setLoginAccount(source.getLoginAccount());
        row.setRealName(source.getRealName());
        row.setDeptName(source.getDeptName());
        String delimiter = Locale.CHINESE.getLanguage().equals(locale.getLanguage()) ? "、" : ", ";
        row.setPostNamesText(source.getPostNames() == null ? "" : String.join(delimiter, source.getPostNames()));
        row.setRoleNamesText(source.getRoleNames() == null ? "" : String.join(delimiter, source.getRoleNames()));
        row.setMobile(source.getMobile());
        row.setEmail(source.getEmail());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setLocked(booleanLabel(source.getLocked(), locale));
        row.setLastLoginAt(source.getLastLoginAt());
        row.setLastLoginIp(source.getLastLoginIp());
        row.setRemark(source.getRemark());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
