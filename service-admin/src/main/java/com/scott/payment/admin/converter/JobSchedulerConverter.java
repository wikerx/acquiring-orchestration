package com.scott.payment.admin.converter;

import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.dto.export.JobRunLogExportRow;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobTaskSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerConverter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度对象转换器，位于 service-admin 转换层；负责后台请求、service-job 内部请求和导出行之间的字段映射。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface JobSchedulerConverter {

    /**
     * 将管理后台保存请求转换为 service-job 内部请求。
     *
     * @param request  管理后台保存请求
     * @param operator 当前操作人
     * @return 内部服务保存请求
     */
    @Mapping(target = "operator", source = "operator")
    JobTaskRemoteSaveRequest toRemoteSaveRequest(JobTaskSaveRequest request, String operator);

    /**
     * 运行日志响应 DTO 转导出行对象。
     *
     * @param response 运行日志响应 DTO
     * @return 导出行对象
     */
    @Mapping(target = "triggerType", ignore = true)
    @Mapping(target = "runStatus", ignore = true)
    JobRunLogExportRow toRunLogExportRow(JobRunLogResponse response);
}
