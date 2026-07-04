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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandlerRegistry
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Handler Registry，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class JobHandlerRegistry {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final Map<String, JobHandler> handlerMap;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param handlerCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param handlerCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    public Collection<JobHandlerDescriptor> listDescriptors() {
        return descriptorMap.values().stream()
                .sorted(Comparator.comparing(JobHandlerDescriptor::getJobGroup)
                        .thenComparing(JobHandlerDescriptor::getHandlerCode))
                .toList();
    }
}
