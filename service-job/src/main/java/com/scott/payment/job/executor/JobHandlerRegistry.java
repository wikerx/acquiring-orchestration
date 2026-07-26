package com.scott.payment.job.executor;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerRegistry
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务处理器注册中心
 * @status : create
 */
@Component
public class JobHandlerRegistry {

    /**
     * handler Map，用于保存 Job Handler Registry 中与 handlermap 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Map<String, JobHandler> handlerMap;
    /**
     * descriptor Map，用于保存 Job Handler Registry 中与 descriptormap 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final Map<String, JobHandlerDescriptor> descriptorMap;

    /**
     * 根据 Spring 容器中的处理器 Bean 构建白名单注册表。
     *
     * @param handlers 任务处理器列表
     */
    public JobHandlerRegistry(List<JobHandler> handlers) {
        this.handlerMap = new LinkedHashMap<>();
        this.descriptorMap = new LinkedHashMap<>();
        for (JobHandler handler : handlers) {
            JobHandlerDescriptor descriptor = handler.descriptor();
            if (descriptor == null || descriptor.getHandlerCode() == null || descriptor.getHandlerCode().isBlank()) {
                throw new IllegalStateException("job handler descriptor.handlerCode must not be blank");
            }
            if (handlerMap.containsKey(descriptor.getHandlerCode())) {
                throw new IllegalStateException("duplicated job handler code: " + descriptor.getHandlerCode());
            }
            handlerMap.put(descriptor.getHandlerCode(), handler);
            descriptorMap.put(descriptor.getHandlerCode(), descriptor);
        }
    }

    /**
     * 按编码查询任务处理器。
     *
     * @param handlerCode 处理器编码
     * @return 任务处理器
     */
    public JobHandler getRequiredHandler(String handlerCode) {
        JobHandler handler = handlerMap.get(handlerCode);
        if (handler == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "job handler not found: " + handlerCode);
        }
        return handler;
    }

    /**
     * 查询处理器描述。
     *
     * @param handlerCode 处理器编码
     * @return 处理器描述
     */
    public JobHandlerDescriptor getRequiredDescriptor(String handlerCode) {
        JobHandlerDescriptor descriptor = descriptorMap.get(handlerCode);
        if (descriptor == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "job handler descriptor not found: " + handlerCode);
        }
        return descriptor;
    }

    /**
     * 返回全部处理器描述，供后台下拉列表使用。
     *
     * @return 处理器描述集合
     */
    public Collection<JobHandlerDescriptor> listDescriptors() {
        return descriptorMap.values().stream()
                .sorted(Comparator.comparing(JobHandlerDescriptor::getJobGroup)
                        .thenComparing(JobHandlerDescriptor::getHandlerCode))
                .toList();
    }
}
