package com.scott.payment.component.excel.support;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelTitleWriteHandler
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 标题与导出元信息写入处理器
 * @status : create
 *
 * <p>通过在真正写表头前预留两行元信息，统一输出报表标题、导出时间、导出人和查询条件，
 * 避免写入后再整体挪动数据行导致 EasyExcel 内容错位或空数据。</p>
 */
public class ExcelTitleWriteHandler implements SheetWriteHandler {

    /**
     * 标题行索引。
     */
    public static final int TITLE_ROW_INDEX = 0;

    /**
     * 信息行索引。
     */
    public static final int META_ROW_INDEX = 1;

    /**
     * 表头行索引。
     */
    public static final int HEADER_ROW_INDEX = 2;

    /**
     * 需要在表头前额外预留的行数。
     */
    public static final int HEADER_ROW_OFFSET = 2;

    /**
     * 时间格式。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * title，用于保存 Excel Title Write Handler 中与 title 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String title;

    /**
     * operator，用于保存 Excel Title Write Handler 中与 operator 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String operator;

    /**
     * export Time，用于保存 Excel Title Write Handler 中与 exporttime 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final LocalDateTime exportTime;

    /**
     * query Summary，用于保存 Excel Title Write Handler 中与 query汇总 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String querySummary;

    /**
     * column Size，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private final int columnSize;

    /**
     * message Resolver，用于保存 Excel Title Write Handler 中与 messageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver messageResolver;

    /**
     * locale，用于保存 Excel Title Write Handler 中与 locale 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Locale locale;

    /**
     * initialized，用于保存 Excel Title Write Handler 中与 initialized 相关的业务属性。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private boolean initialized;

    /**
     * 创建标题处理器。
     *
     * @param title           标题
     * @param operator        导出人
     * @param exportTime      导出时间
     * @param querySummary    查询条件摘要
     * @param columnSize      列数
     * @param messageResolver 文案解析器
     * @param locale          导出语言
     */
    public ExcelTitleWriteHandler(String title,
                                  String operator,
                                  LocalDateTime exportTime,
                                  String querySummary,
                                  int columnSize,
                                  ExcelI18nMessageResolver messageResolver,
                                  Locale locale) {
        this.title = title;
        this.operator = operator;
        this.exportTime = exportTime;
        this.querySummary = querySummary;
        this.columnSize = Math.max(columnSize, 1);
        this.messageResolver = messageResolver;
        this.locale = locale;
    }

    /**
     * 在 sheet 创建后写入标题和导出元信息。
     *
     * @param writeWorkbookHolder 当前工作簿上下文
     * @param writeSheetHolder    当前 sheet 上下文
     */
    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder,
                                 WriteSheetHolder writeSheetHolder) {
        if (initialized) {
            return;
        }
        Sheet sheet = writeSheetHolder.getSheet();
        writeTitle(sheet);
        writeMeta(sheet);
        initialized = true;
        sheet.createFreezePane(0, HEADER_ROW_INDEX + 1);
    }

    /**
     * 写入标题行。
     *
     * @param sheet 当前 sheet
     */
    private void writeTitle(Sheet sheet) {
        Row titleRow = getOrCreateRow(sheet, TITLE_ROW_INDEX);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(buildTitleStyle(sheet));
        sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW_INDEX, TITLE_ROW_INDEX, 0, columnSize - 1));
    }

    /**
     * 写入导出元信息。
     *
     * @param sheet 当前 sheet
     */
    private void writeMeta(Sheet sheet) {
        Row metaRow = getOrCreateRow(sheet, META_ROW_INDEX);
        Cell metaCell = metaRow.createCell(0);
        metaCell.setCellValue(buildMetaText());
        metaCell.setCellStyle(buildMetaStyle(sheet));
        sheet.addMergedRegion(new CellRangeAddress(META_ROW_INDEX, META_ROW_INDEX, 0, columnSize - 1));
    }

    /**
     * 构造元信息文本。
     *
     * @return 元信息文本
     */
    private String buildMetaText() {
        String exportTimeLabel = messageResolver.resolve("excel.common.exportTime", locale);
        String operatorLabel = messageResolver.resolve("excel.common.operator", locale);
        String querySummaryLabel = messageResolver.resolve("excel.common.querySummary", locale);
        String defaultSummary = messageResolver.resolve("excel.common.noCondition", locale);
        String safeSummary = querySummary == null || querySummary.isBlank() ? defaultSummary : querySummary;
        return exportTimeLabel + ": " + DATE_TIME_FORMATTER.format(exportTime)
                + "    " + operatorLabel + ": " + (operator == null || operator.isBlank() ? "-" : operator)
                + "    " + querySummaryLabel + ": " + safeSummary;
    }

    /**
     * 获取或创建行对象。
     *
     * @param sheet sheet
     * @param rowIndex 行号
     * @return 行对象
     */
    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    /**
     * 构造标题样式。
     *
     * @param sheet sheet
     * @return 样式
     */
    private CellStyle buildTitleStyle(Sheet sheet) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        titleFont(sheet, style);
        return style;
    }

    /**
     * 标题字体设置。
     *
     * @param sheet sheet
     * @param style 样式
     */
    private void titleFont(Sheet sheet, CellStyle style) {
        var font = sheet.getWorkbook().createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
    }

    /**
     * 构造元信息样式。
     *
     * @param sheet sheet
     * @return 样式
     */
    private CellStyle buildMetaStyle(Sheet sheet) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }
}
