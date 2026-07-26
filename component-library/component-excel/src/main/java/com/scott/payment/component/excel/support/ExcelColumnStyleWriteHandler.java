package com.scott.payment.component.excel.support;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.constant.OrderConstant;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelColumnStyleWriteHandler
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : Excel 列级内容样式处理器，按导出列定义统一控制内容对齐方式和边框。
 * @status : create
 */
public class ExcelColumnStyleWriteHandler implements CellWriteHandler {

    /**
     * alignments，用于保存 Excel Column Style Write Handler 中与 alignments 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Map<Integer, HorizontalAlignment> alignments = new HashMap<>();

    /**
     * 创建列级样式处理器。
     *
     * @param columns 导出列定义
     */
    public ExcelColumnStyleWriteHandler(List<ExcelExportColumnDefinition> columns) {
        for (int index = 0; index < columns.size(); index++) {
            HorizontalAlignment alignment = columns.get(index).align();
            alignments.put(index, alignment == null ? HorizontalAlignment.LEFT : alignment);
        }
    }

    /**
     * 确保列级样式在默认表格样式之后合并，避免被统一内容样式覆盖。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return OrderConstant.DEFINE_STYLE + 1;
    }

    /**
     * 在单元格写入后补齐列级内容样式。
     *
     * @param context 单元格写入上下文
     */
    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        applyCellStyle(context.getFirstCellData(), context.getColumnIndex(), context.getHead(), context.getRelativeRowIndex());
    }

    /**
     * 在单元格写入后补齐列级内容样式。
     *
     * @param writeSheetHolder 当前 sheet 写入上下文
     * @param writeTableHolder 当前表格写入上下文
     * @param cellDataList     单元格写入数据
     * @param cell             当前单元格
     * @param head             表头定义
     * @param relativeRowIndex 相对行号
     * @param isHead           是否表头
     */
    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder,
                                 WriteTableHolder writeTableHolder,
                                 List<WriteCellData<?>> cellDataList,
                                 Cell cell,
                                 Head head,
                                 Integer relativeRowIndex,
                                 Boolean isHead) {
        WriteCellData<?> cellData = cellDataList == null || cellDataList.isEmpty() ? null : cellDataList.get(0);
        applyCellStyle(cellData, cell.getColumnIndex(), isHead, relativeRowIndex);
    }

    /**
     * 对非表头单元格应用统一内容样式。
     *
     * @param cellData 当前单元格写入数据
     * @param columnIndex 列号
     * @param isHead 是否表头
     * @param relativeRowIndex 相对行号
     */
    private void applyCellStyle(WriteCellData<?> cellData,
                                Integer columnIndex,
                                Boolean isHead,
                                Integer relativeRowIndex) {
        if (cellData == null || Boolean.TRUE.equals(isHead) || relativeRowIndex == null) {
            return;
        }
        WriteCellStyle style = new WriteCellStyle();
        style.setHorizontalAlignment(alignments.getOrDefault(columnIndex, HorizontalAlignment.LEFT));
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapped(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        WriteCellStyle.merge(style, cellData.getOrCreateStyle());
    }
}
