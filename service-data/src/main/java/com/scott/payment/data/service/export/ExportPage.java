package com.scott.payment.data.service.export;

import java.util.List;

/**
 * 权威来源服务返回的单页导出数据和相同条件下总行数。
 *
 * @param total 总行数
 * @param records 当前页记录
 * @param <T> 导出行类型
 */
public record ExportPage<T>(long total, List<T> records) {

    /**
     * 规整空记录集合，避免 Excel 分页回调处理空指针。
     */
    public ExportPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
