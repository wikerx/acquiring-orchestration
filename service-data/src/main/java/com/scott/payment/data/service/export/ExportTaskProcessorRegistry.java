package com.scott.payment.data.service.export;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步导出处理器注册表，启动时拒绝空类型和重复类型，执行时按稳定任务类型路由。
 */
@Component
public class ExportTaskProcessorRegistry {
    private final Map<String, ExportTaskProcessor> processors;

    /**
     * 从 Spring 管理的处理器构建不可变类型索引。
     *
     * @param candidates 全部导出处理器
     */
    public ExportTaskProcessorRegistry(List<ExportTaskProcessor> candidates) {
        Map<String, ExportTaskProcessor> indexed = new LinkedHashMap<>();
        for (ExportTaskProcessor processor : candidates) {
            if (processor == null || !StringUtils.hasText(processor.taskType())) {
                throw new IllegalStateException("export task processor type is required");
            }
            ExportTaskProcessor duplicate = indexed.putIfAbsent(processor.taskType(), processor);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate export task processor: " + processor.taskType());
            }
        }
        this.processors = Map.copyOf(indexed);
    }

    /**
     * 返回指定任务类型的处理器，未知类型必须失败而不能误用其他导出逻辑。
     *
     * @param taskType 稳定任务类型
     * @return 匹配的处理器
     */
    public ExportTaskProcessor required(String taskType) {
        ExportTaskProcessor processor = processors.get(taskType);
        if (processor == null) {
            throw new IllegalStateException("unsupported export task type");
        }
        return processor;
    }
}
