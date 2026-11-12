package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.HolidayCalendarQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.HolidayCalendarExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_HOLIDAY_CALENDAR;

/**
 * 管理端节假日日历异步导出处理器，按冻结自然年读取 Admin 安全投影。
 */
@Service
public class HolidayCalendarExportProcessor
        extends AbstractBaseReferenceExportProcessor<HolidayCalendarQuery, HolidayCalendarExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建节假日日历异步导出处理器并注入权威数据源与统一文件写入能力。
     */
    public HolidayCalendarExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                          ExcelExportArtifactWriter artifactWriter,
                                          ExcelI18nMessageResolver messageResolver,
                                          DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回节假日日历导出的稳定任务类型，供任务分发器选择当前处理器。
     */
    @Override
    public String taskType() {
        return ADMIN_HOLIDAY_CALENDAR;
    }

    /**
     * 返回日历查询快照类型，确保自然年按强类型反序列化。
     */
    @Override
    protected Class<HolidayCalendarQuery> queryClass() {
        return HolidayCalendarQuery.class;
    }

    /**
     * 返回节假日日历 Excel 标题的国际化资源键。
     */
    @Override
    protected String titleKey() {
        return "excel.calendar.title";
    }

    /**
     * 返回节假日日历文件名的中文兜底标题。
     */
    @Override
    protected String chineseTitle() {
        return "节假日日历";
    }

    /**
     * 返回节假日日历文件名的英文兜底标题。
     */
    @Override
    protected String englishTitle() {
        return "Holiday Calendar";
    }

    /**
     * 返回节假日日历专用 Excel 行模型，避免内部日期对象直接进入文件层。
     */
    @Override
    protected Class<HolidayCalendarExportRow> rowClass() {
        return HolidayCalendarExportRow.class;
    }

    /**
     * 按冻结自然年分页读取日历安全投影，并转换为当前任务语言的 Excel 行。
     */
    @Override
    protected ExportPage<HolidayCalendarExportRow> loadPage(
            DataExportTaskDO task, HolidayCalendarQuery query, int pageNo, Locale locale) {
        HolidayCalendarPageRequest request = new HolidayCalendarPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        HolidayCalendarPageResponse response = sourceClient.loadHolidayCalendarPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<HolidayCalendarExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将日历安全投影转换为 Excel 行，并本地化法定节假日与调休标记。
     */
    private HolidayCalendarExportRow toExportRow(HolidayCalendarRow source, Locale locale) {
        HolidayCalendarExportRow row = new HolidayCalendarExportRow();
        row.setCalendarDate(source.getCalendarDate());
        row.setDayOfWeek(source.getDayOfWeek());
        row.setDayType(source.getDayType());
        row.setHolidayName(source.getHolidayName());
        row.setStatutoryHoliday(booleanLabel(source.getStatutoryHoliday(), locale));
        row.setAdjustedWorkday(booleanLabel(source.getAdjustedWorkday(), locale));
        row.setRemark(source.getRemark());
        row.setUpdateBy(source.getUpdateBy());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
