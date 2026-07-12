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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务调度对象转换器，位于 service-job 转换层；负责调度任务、运行日志、执行节点和处理器选项的普通字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface JobSchedulerConverter {

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
