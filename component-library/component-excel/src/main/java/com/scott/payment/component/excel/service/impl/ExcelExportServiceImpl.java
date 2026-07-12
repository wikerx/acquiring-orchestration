package com.scott.payment.component.excel.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterSheetBuilder;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelColumnStyleWriteHandler;
import com.scott.payment.component.excel.support.ExcelColumnWidthWriteHandler;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.component.excel.support.ExcelExportColumnDefinition;
import com.scott.payment.component.excel.support.ExcelExportMetadataResolver;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelStyleStrategyFactory;
import com.scott.payment.component.excel.support.ExcelTitleWriteHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportServiceImpl
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出服务实现
 * @status : create
 *
 * <p>统一处理响应头、导出标题、表头国际化、列宽样式和空数据导出，
 * 让业务代码只负责准备导出 DTO 数据。</p>
 */
@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    /**
     * 导出时间统一格式。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelExportMetadataResolver metadataResolver;
    private final ExcelStyleStrategyFactory styleStrategyFactory;

    /**
     * 创建 Excel 导出服务实现。
     *
     * @param messageResolver      国际化文案解析器
     * @param metadataResolver     导出列元数据解析器
     * @param styleStrategyFactory 样式策略工厂
     */
    public ExcelExportServiceImpl(ExcelI18nMessageResolver messageResolver,
                                  ExcelExportMetadataResolver metadataResolver,
                                  ExcelStyleStrategyFactory styleStrategyFactory) {
        this.messageResolver = messageResolver;
        this.metadataResolver = metadataResolver;
        this.styleStrategyFactory = styleStrategyFactory;
    }

    /**
     * 按注解列定义导出统一样式 Excel。
     *
     * @param request 导出请求
     * @param response HTTP 响应
     * @param <T> 行类型
     */
    @Override
    public <T> void export(ExcelExportRequest<T> request, HttpServletResponse response) {
        Locale locale = resolveLocale(request);
        List<ExcelExportColumnDefinition> columns = metadataResolver.resolveColumns(request.getRowClass());
        List<List<Object>> rows = toRowData(request.getDataList(), columns, locale);
        List<List<String>> head = buildHead(columns, locale);
        String fileName = normalizeFileName(request.getFileName());
        String sheetName = normalizeSheetName(request.getSheetName());
        String title = messageResolver.resolve(request.getTitleKey(), locale);
        try {
            prepareResponse(response, fileName);
            ExcelWriterSheetBuilder sheetBuilder = EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .relativeHeadRowIndex(ExcelTitleWriteHandler.HEADER_ROW_OFFSET)
                    .registerWriteHandler(styleStrategyFactory.createDefaultStrategy())
                    .registerWriteHandler(new ExcelColumnStyleWriteHandler(columns))
                    .registerWriteHandler(new ExcelColumnWidthWriteHandler(columns))
                    .registerWriteHandler(new ExcelTitleWriteHandler(
                            title,
                            request.getOperator(),
                            request.getExportTime() == null ? LocalDateTime.now() : request.getExportTime(),
                            request.getQuerySummary(),
                            Math.max(columns.size(), 1),
                            messageResolver,
                            locale
                    ))
                    .sheet(sheetName);
            sheetBuilder.doWrite(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("excel export failed", exception);
        }
    }

    /**
     * 使用动态列定义导出 Excel，适用于不同功能拥有不同列结构的管理端列表导出。
     *
     * @param request 动态列导出请求
     * @param response HTTP 响应
     */
    @Override
    public void exportDynamic(ExcelDynamicExportRequest request, HttpServletResponse response) {
        Locale locale = request.getLocale() == null ? resolveCurrentLocale() : request.getLocale();
        List<ExcelDynamicColumnDefinition> columns = request.getColumns() == null ? List.of() : request.getColumns();
        List<List<String>> head = buildDynamicHead(columns);
        List<List<Object>> rows = toDynamicRowData(request.getDataList(), columns, locale);
        List<ExcelExportColumnDefinition> columnDefinitions = toColumnDefinitions(columns);
        String fileName = normalizeFileName(request.getFileName());
        String sheetName = normalizeSheetName(request.getSheetName());
        String title = request.getTitle() == null || request.getTitle().isBlank() ? sheetName : request.getTitle().trim();
        try {
            prepareResponse(response, fileName);
            ExcelWriterSheetBuilder sheetBuilder = EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .relativeHeadRowIndex(ExcelTitleWriteHandler.HEADER_ROW_OFFSET)
                    .registerWriteHandler(styleStrategyFactory.createDefaultStrategy())
                    .registerWriteHandler(new ExcelColumnStyleWriteHandler(columnDefinitions))
                    .registerWriteHandler(new ExcelColumnWidthWriteHandler(columnDefinitions))
                    .registerWriteHandler(new ExcelTitleWriteHandler(
                            title,
                            request.getOperator(),
                            request.getExportTime() == null ? LocalDateTime.now() : request.getExportTime(),
                            request.getQuerySummary(),
                            Math.max(columns.size(), 1),
                            messageResolver,
                            locale
                    ))
                    .sheet(sheetName);
            sheetBuilder.doWrite(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("excel export failed", exception);
        }
    }

    /**
     * 将导出对象列表转换为按列顺序排列的 Map 行数据。
     *
     * @param dataList 导出对象列表
     * @param columns 列定义
     * @param <T> 行类型
     * @return Map 行数据
     */
    private <T> List<List<Object>> toRowData(List<T> dataList,
                                             List<ExcelExportColumnDefinition> columns,
                                             Locale locale) {
        if (dataList == null || dataList.isEmpty()) {
            return List.of();
        }
        List<List<Object>> rows = new ArrayList<>(dataList.size());
        for (T data : dataList) {
            List<Object> row = new ArrayList<>(columns.size());
            for (ExcelExportColumnDefinition column : columns) {
                row.add(readValue(data, column.field(), locale));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 构造 EasyExcel 表头结构。
     *
     * @param columns 列定义
     * @param locale 当前语言
     * @return 表头结构
     */
    private List<List<String>> buildHead(List<ExcelExportColumnDefinition> columns, Locale locale) {
        List<List<String>> head = new ArrayList<>(columns.size());
        for (ExcelExportColumnDefinition column : columns) {
            head.add(List.of(messageResolver.resolve(column.headerKey(), locale)));
        }
        return head;
    }

    /**
     * 构造动态列表头。
     *
     * @param columns 动态列定义
     * @return 表头结构
     */
    private List<List<String>> buildDynamicHead(List<ExcelDynamicColumnDefinition> columns) {
        List<List<String>> head = new ArrayList<>(columns.size());
        for (ExcelDynamicColumnDefinition column : columns) {
            head.add(List.of(column.header()));
        }
        return head;
    }

    /**
     * 将动态行数据转换为 EasyExcel 行数据。
     *
     * @param dataList 原始行数据
     * @param columns 动态列定义
     * @param locale 导出语言
     * @return EasyExcel 行数据
     */
    private List<List<Object>> toDynamicRowData(List<Map<String, Object>> dataList,
                                                List<ExcelDynamicColumnDefinition> columns,
                                                Locale locale) {
        if (dataList == null || dataList.isEmpty()) {
            return List.of();
        }
        List<List<Object>> rows = new ArrayList<>(dataList.size());
        for (Map<String, Object> data : dataList) {
            List<Object> row = new ArrayList<>(columns.size());
            for (ExcelDynamicColumnDefinition column : columns) {
                row.add(formatValue(data.get(column.key()), locale));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将动态列定义转换为列宽处理器所需的通用列定义。
     *
     * @param columns 动态列定义
     * @return 列宽定义
     */
    private List<ExcelExportColumnDefinition> toColumnDefinitions(List<ExcelDynamicColumnDefinition> columns) {
        return columns.stream()
                .map(column -> new ExcelExportColumnDefinition(null, 0, "", column.width(), column.align()))
                .toList();
    }

    /**
     * 读取导出字段值。
     *
     * @param data 数据对象
     * @param field 字段
     * @return 字段值
     */
    private Object readValue(Object data, Field field, Locale locale) {
        try {
            Object value = field.get(data);
            return formatValue(value, locale);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("read export field failed: " + field.getName(), exception);
        }
    }

    /**
     * 按统一规则格式化导出单元格值。
     *
     * @param value 原始值
     * @param locale 导出语言
     * @return Excel 单元格值
     */
    private Object formatValue(Object value, Locale locale) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime localDateTime) {
            return DATE_TIME_FORMATTER.format(localDateTime);
        }
        if (value instanceof Instant instant) {
            return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        }
        if (value instanceof Boolean booleanValue) {
            return messageResolver.resolve(booleanValue ? "excel.common.yes" : "excel.common.no", locale);
        }
        return value;
    }

    /**
     * 准备下载响应头。
     *
     * @param response HTTP 响应
     * @param fileName 文件名
     * @throws IOException 编码异常
     */
    private void prepareResponse(HttpServletResponse response, String fileName) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(
                "Content-Disposition",
                "attachment;filename*=utf-8''" + URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8)
        );
    }

    /**
     * 规整下载文件名。
     *
     * @param fileName 文件名
     * @return 规整结果
     */
    private String normalizeFileName(String fileName) {
        String value = fileName == null || fileName.isBlank() ? "export" : fileName.trim();
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 规整 sheet 名称。
     *
     * @param sheetName sheet 名称
     * @return 规整结果
     */
    private String normalizeSheetName(String sheetName) {
        String value = sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName.trim();
        String normalized = value.replaceAll("[\\\\/:*?\\[\\]]", "_");
        return normalized.length() > 31 ? normalized.substring(0, 31) : normalized;
    }

    /**
     * 解析本次导出语言环境。
     *
     * @return 语言环境
     */
    private <T> Locale resolveLocale(ExcelExportRequest<T> request) {
        if (request.getLocale() != null) {
            return request.getLocale();
        }
        return resolveCurrentLocale();
    }

    /**
     * 解析当前请求语言环境。
     *
     * @return 当前语言环境
     */
    private Locale resolveCurrentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
