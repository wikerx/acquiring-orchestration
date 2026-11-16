package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MccCodeQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.MccCodeExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MCC_CODE;

/**
 * 管理端 MCC 编码异步导出处理器，完整保留树列表冻结筛选条件。
 */
@Service
public class MccCodeExportProcessor
        extends AbstractBaseReferenceExportProcessor<MccCodeQuery, MccCodeExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建 MCC 编码异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化解析器
     * @param properties 异步导出容量配置
     */
    public MccCodeExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                  ExcelExportArtifactWriter artifactWriter,
                                  ExcelI18nMessageResolver messageResolver,
                                  DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回 MCC 编码稳定任务类型。
     *
     * @return 任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_MCC_CODE;
    }

    /**
     * 返回 MCC 编码查询快照类型。
     *
     * @return 查询类型
     */
    @Override
    protected Class<MccCodeQuery> queryClass() {
        return MccCodeQuery.class;
    }

    /**
     * 返回 MCC 编码 Excel 标题键。
     *
     * @return 标题键
     */
    @Override
    protected String titleKey() {
        return "excel.mcc.title";
    }

    /**
     * 返回 MCC 编码中文标题。
     *
     * @return 中文标题
     */
    @Override
    protected String chineseTitle() {
        return "MCC 编码";
    }

    /**
     * 返回 MCC 编码英文标题。
     *
     * @return 英文标题
     */
    @Override
    protected String englishTitle() {
        return "MCC Codes";
    }

    /**
     * 返回 MCC 编码 Excel 行类型。
     *
     * @return 行类型
     */
    @Override
    protected Class<MccCodeExportRow> rowClass() {
        return MccCodeExportRow.class;
    }

    /**
     * 按冻结筛选读取一页 MCC 编码并本地化状态。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 页码
     * @param locale 冻结语言
     * @return 导出分页
     */
    @Override
    protected ExportPage<MccCodeExportRow> loadPage(
            DataExportTaskDO task, MccCodeQuery query, int pageNo, Locale locale) {
        MccCodePageRequest request = new MccCodePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        MccCodePageResponse response = sourceClient.loadMccCodePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<MccCodeExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将 Admin 安全投影转换为任务冻结语言下的 Excel 行。
     *
     * @param source Admin 安全投影
     * @param locale 冻结语言
     * @return Excel 行
     */
    private MccCodeExportRow toExportRow(MccCodeRow source, Locale locale) {
        MccCodeExportRow row = new MccCodeExportRow();
        row.setMccCode(source.getMccCode());
        row.setNameCn(source.getNameCn());
        row.setNameEn(source.getNameEn());
        row.setLevel1Name(source.getLevel1Name());
        row.setLevel2Name(source.getLevel2Name());
        row.setMccType(source.getMccType());
        row.setRiskLevel(source.getRiskLevel());
        row.setDeliveryApplicability(source.getDeliveryApplicability());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setSource(source.getSource());
        row.setVersionNo(source.getVersionNo());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setRemark(source.getRemark());
        return row;
    }
}
