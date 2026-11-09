package com.scott.payment.data.service.export;

import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;

import java.time.LocalDateTime;

/**
 * 单一异步导出任务类型处理器，负责解释冻结快照并生成最终文件。
 */
public interface ExportTaskProcessor {

    /**
     * 返回处理器唯一支持的稳定任务类型。
     *
     * @return 导出任务类型
     */
    String taskType();

    /**
     * 按任务创建时冻结的条件和范围生成、校验并上传文件。
     *
     * @param task 已领取的导出任务
     * @param storage 私有对象存储
     * @param progressListener 分页进度监听器
     * @param now 本轮执行时间
     * @return 不可变文件元数据
     */
    ExportArtifact process(DataExportTaskDO task,
                           ExportObjectStorageService storage,
                           ExportProgressListener progressListener,
                           LocalDateTime now);
}
