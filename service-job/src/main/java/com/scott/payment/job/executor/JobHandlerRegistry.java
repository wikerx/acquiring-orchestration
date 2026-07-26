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
     * handler Map 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final Map<String, JobHandler> handlerMap;
    /**
     * descriptor Map 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
