package com.scott.payment.data.service.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 导出处理器注册表测试，覆盖稳定路由、重复类型和未知类型失败行为。
 */
class ExportTaskProcessorRegistryTest {

    /**
     * 已注册任务类型必须返回其唯一处理器。
     */
    @Test
    void shouldResolveRegisteredProcessor() {
        ExportTaskProcessor processor = mock(ExportTaskProcessor.class);
        when(processor.taskType()).thenReturn("ADMIN_TRANSACTION_ORDER");
        ExportTaskProcessorRegistry registry = new ExportTaskProcessorRegistry(List.of(processor));

        assertThat(registry.required("ADMIN_TRANSACTION_ORDER")).isSameAs(processor);
    }

    /**
     * 重复任务类型必须在启动装配阶段失败。
     */
    @Test
    void shouldRejectDuplicateTaskTypes() {
        ExportTaskProcessor first = mock(ExportTaskProcessor.class);
        ExportTaskProcessor second = mock(ExportTaskProcessor.class);
        when(first.taskType()).thenReturn("ADMIN_REFUND");
        when(second.taskType()).thenReturn("ADMIN_REFUND");

        assertThatThrownBy(() -> new ExportTaskProcessorRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    /**
     * 未知任务类型必须失败，不能静默回退到其他导出逻辑。
     */
    @Test
    void shouldRejectUnknownTaskType() {
        ExportTaskProcessorRegistry registry = new ExportTaskProcessorRegistry(List.of());

        assertThatThrownBy(() -> registry.required("UNKNOWN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported");
    }
}
