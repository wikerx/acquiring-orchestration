package com.scott.payment.job.converter;

import com.scott.payment.job.api.internal.dto.JobExecutorNodeResponse;
import com.scott.payment.job.api.internal.dto.JobHandlerOptionResponse;
import com.scott.payment.job.api.internal.dto.JobRunLogResponse;
import com.scott.payment.job.api.internal.dto.JobTaskResponse;
import com.scott.payment.job.entity.SysJobExecutorNodeDO;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务调度对象转换器
 * @status : create
 */

@Mapper
public interface JobSchedulerConverter {

    /**
     * MapStruct 转换器实例。
     */
    JobSchedulerConverter INSTANCE = Mappers.getMapper(JobSchedulerConverter.class);

    /**
     * 任务实体转响应对象。
     *
     * @param entity 任务实体
     * @return 任务响应
     */
    JobTaskResponse toTaskResponse(SysJobTaskDO entity);

    /**
     * 运行日志实体转响应对象。
     *
     * @param entity 运行日志实体
     * @return 运行日志响应
     */
    JobRunLogResponse toRunLogResponse(SysJobRunLogDO entity);

    /**
     * 节点实体转响应对象。
     *
     * @param entity 节点实体
     * @return 节点响应
     */
    JobExecutorNodeResponse toNodeResponse(SysJobExecutorNodeDO entity);

    /**
     * 处理器描述转下拉选项。
     *
     * @param descriptor 处理器描述
     * @return 处理器选项
     */
    JobHandlerOptionResponse toHandlerOption(JobHandlerDescriptor descriptor);
}
