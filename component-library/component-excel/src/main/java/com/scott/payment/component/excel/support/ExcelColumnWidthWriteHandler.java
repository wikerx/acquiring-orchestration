package com.scott.payment.component.excel.support;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.style.column.AbstractColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelColumnWidthWriteHandler
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 列宽控制处理器
 * @status : create
 *
 * <p>优先根据列定义设置建议列宽，并根据内容长度做增量放宽，
 * 避免导出文件出现大量文本被截断。</p>
 */
public class ExcelColumnWidthWriteHandler extends AbstractColumnWidthStyleStrategy {

    /**
     * 最小列宽。
     */
    private static final int MIN_WIDTH = 12;

    /**
     * 最大列宽。
     */
    private static final int MAX_WIDTH = 48;

    /**
     * base Widths，用于保存 Excel Column Width Write Handler 中与 basewidths 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Map<Integer, Integer> baseWidths = new HashMap<>();
    /**
     * current Widths，用于保存 Excel Column Width Write Handler 中与 currentwidths 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Map<Integer, Integer> currentWidths = new HashMap<>();

    /**
     * 创建列宽处理器。
     *
     * @param columns 列定义
     */
    public ExcelColumnWidthWriteHandler(List<ExcelExportColumnDefinition> columns) {
        for (int index = 0; index < columns.size(); index++) {
            baseWidths.put(index, columns.get(index).width());
        }
    }

    /**
     * 根据列定义和单元格内容动态调整列宽。
     *
     * @param writeSheetHolder 当前 sheet 写入上下文
     * @param cellDataList 单元格写入数据
     * @param cell 当前单元格
     * @param head 表头定义
     * @param relativeRowIndex 相对行号
     * @param isHead 是否表头
     */
    @Override
    protected void setColumnWidth(WriteSheetHolder writeSheetHolder,
                                  List<WriteCellData<?>> cellDataList,
                                  Cell cell,
                                  Head head,
                                  Integer relativeRowIndex,
                                  Boolean isHead) {
        int columnIndex = cell.getColumnIndex();
        int targetWidth = Math.max(MIN_WIDTH, baseWidths.getOrDefault(columnIndex, MIN_WIDTH));
        String cellValue = extractCellText(cell);
        if (!cellValue.isBlank()) {
            targetWidth = Math.max(targetWidth, Math.min(MAX_WIDTH, cellValue.length() + 4));
        }
        int currentWidth = currentWidths.getOrDefault(columnIndex, 0);
        if (targetWidth > currentWidth) {
            writeSheetHolder.getSheet().setColumnWidth(columnIndex, targetWidth * 256);
            currentWidths.put(columnIndex, targetWidth);
        }
    }

    /**
     * 读取单元格文本内容，兼容字符串、数字和布尔等常见类型。
     *
     * @param cell 当前单元格
     * @return 单元格文本
     */
    private String extractCellText(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
