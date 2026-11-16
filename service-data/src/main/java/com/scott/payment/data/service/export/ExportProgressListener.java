package com.scott.payment.data.service.export;

/**
 * 异步导出分页进度监听器，由任务状态机使用租约版本条件持久化累计进度。
 */
@FunctionalInterface
public interface ExportProgressListener {

    /**
     * 保存已处理行数和当前权威总数。
     *
     * @param processedRows 已处理行数
     * @param totalRows 来源服务返回的权威总数
     */
    void onProgress(long processedRows, long totalRows);
}
