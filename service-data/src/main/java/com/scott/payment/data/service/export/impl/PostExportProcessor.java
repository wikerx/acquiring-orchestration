package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PostQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.PostExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_POST;

/**
 * 管理端岗位异步导出处理器，按冻结筛选条件读取 Admin 安全投影。
 */
@Service
public class PostExportProcessor extends AbstractBaseReferenceExportProcessor<PostQuery, PostExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建岗位异步导出处理器并注入权威数据源与统一文件写入能力。
     */
    public PostExportProcessor(AdminPlatformExportSourceClient sourceClient,
                               ExcelExportArtifactWriter artifactWriter,
                               ExcelI18nMessageResolver messageResolver,
                               DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回岗位导出的稳定任务类型，供任务分发器选择当前处理器。
     */
    @Override
    public String taskType() {
        return ADMIN_POST;
    }

    /**
     * 返回岗位查询快照类型，确保任务筛选条件按强类型反序列化。
     */
    @Override
    protected Class<PostQuery> queryClass() {
        return PostQuery.class;
    }

    /**
     * 返回岗位 Excel 标题的国际化资源键。
     */
    @Override
    protected String titleKey() {
        return "excel.post.title";
    }

    /**
     * 返回岗位文件名的中文兜底标题。
     */
    @Override
    protected String chineseTitle() {
        return "岗位";
    }

    /**
     * 返回岗位文件名的英文兜底标题。
     */
    @Override
    protected String englishTitle() {
        return "Posts";
    }

    /**
     * 返回岗位专用 Excel 行模型，保持岗位编码的文本语义。
     */
    @Override
    protected Class<PostExportRow> rowClass() {
        return PostExportRow.class;
    }

    /**
     * 按冻结筛选条件分页读取岗位安全投影，并转换为当前任务语言的 Excel 行。
     */
    @Override
    protected ExportPage<PostExportRow> loadPage(
            DataExportTaskDO task, PostQuery query, int pageNo, Locale locale) {
        PostPageRequest request = new PostPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        PostPageResponse response = sourceClient.loadPostPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<PostExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将岗位安全投影转换为 Excel 行，并本地化启停状态。
     */
    private PostExportRow toExportRow(PostRow source, Locale locale) {
        PostExportRow row = new PostExportRow();
        row.setPostCode(source.getPostCode());
        row.setPostName(source.getPostName());
        row.setSortNo(source.getSortNo());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setRemark(source.getRemark());
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
