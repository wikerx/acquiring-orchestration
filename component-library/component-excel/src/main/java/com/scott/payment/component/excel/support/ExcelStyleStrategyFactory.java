package com.scott.payment.component.excel.support;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelStyleStrategyFactory
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 统一样式策略工厂
 * @status : create
 *
 * <p>统一定义标题、表头和内容区样式，避免每个导出接口单独拼装样式导致风格分裂。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelStyleStrategyFactory
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Excel Style Strategy Factory，位于 component-library/component-excel 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class ExcelStyleStrategyFactory {

    /**
     * 创建表头与内容样式策略。
     *
     * @return 样式策略
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @return 处理后的业务结果或页面展示数据。
     */
    public HorizontalCellStyleStrategy createDefaultStrategy() {
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headStyle.setWrapped(true);
        applyBorder(headStyle);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setWrapped(true);
        applyBorder(contentStyle);
        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    /**
     * 为单元格样式补齐通用边框。
     *
     * @param style 单元格样式
     */
    private void applyBorder(WriteCellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
