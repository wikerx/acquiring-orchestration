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

    private final Map<Integer, Integer> baseWidths = new HashMap<>();
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
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param writeSheetHolder 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param cellDataList 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param cell 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param head 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param relativeRowIndex 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param isHead 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
