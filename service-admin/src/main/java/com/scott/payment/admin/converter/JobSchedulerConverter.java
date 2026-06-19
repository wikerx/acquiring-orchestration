package com.scott.payment.admin.converter;

import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.dto.monitor.JobTaskSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度对象转换器
 * @status : create
 *
 * <p>负责管理后台任务调度请求与 service-job 内部远程请求之间的转换，
 * 避免控制器或应用服务直接拼装服务间调用 DTO。</p>
 */

@Mapper
public interface JobSchedulerConverter {

    /**
     * 转换器单例。
     */
    JobSchedulerConverter INSTANCE = Mappers.getMapper(JobSchedulerConverter.class);

    /**
     * 将管理后台保存请求转换为 service-job 内部请求。
     *
     * @param request  管理后台保存请求
     * @param operator 当前操作人
     * @return 内部服务保存请求
     */
    @Mapping(target = "operator", source = "operator")
    JobTaskRemoteSaveRequest toRemoteSaveRequest(JobTaskSaveRequest request, String operator);
}
