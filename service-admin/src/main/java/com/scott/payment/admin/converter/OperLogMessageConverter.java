package com.scott.payment.admin.converter;

import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.component.mq.message.OperationLogMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperLogMessageConverter
 * @date : 2026-06-20 01:52
 * @email : scott_x@163.com
 * @description : 操作日志 MQ 消息转换器，位于 service-admin 转换层；负责将公共 MQ 操作日志消息转换为后台落库请求。
 * @status : create
 */
@Mapper(componentModel = "spring")
public interface OperLogMessageConverter {

    /**
     * 将操作日志 MQ 消息转换为后台写库请求。
     *
     * @param message 操作日志 MQ 消息
     * @return 后台落库请求
     */
    @Mapping(target = "moduleName", source = "operationModule")
    @Mapping(target = "operationName", source = "operationName")
    @Mapping(target = "businessType", expression = "java(resolveBusinessType(message.getOperationType()))")
    @Mapping(target = "operUrl", source = "requestUri")
    @Mapping(target = "operIp", source = "clientIp")
    @Mapping(target = "operLocation", ignore = true)
    @Mapping(target = "storeId", source = "storeId")
    @Mapping(target = "userAgent", source = "userAgent")
    @Mapping(target = "requestParam", source = "requestParams")
    @Mapping(target = "costTime", source = "costTimeMs")
    @Mapping(target = "status", source = "operationStatus")
    @Mapping(target = "errorMsg", source = "errorMessage")
    @Mapping(target = "operatorType", expression = "java(resolveOperatorType(message.getOperatorType()))")
    @Mapping(target = "operatedAt", source = "operationTime")
    SysOperLogRecordRequest toRecordRequest(OperationLogMessage message);

    /**
     * 解析业务类型。
     *
     * @param operationType 操作类型编码
     * @return 业务类型
     */
    default Integer resolveBusinessType(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return null;
        }
        return Integer.parseInt(operationType);
    }

    /**
     * 解析操作人类型。
     *
     * @param operatorType 操作人类型字符串
     * @return 操作人类型
     */
    default Integer resolveOperatorType(String operatorType) {
        if (operatorType == null || operatorType.isBlank()) {
            return null;
        }
        return Integer.parseInt(operatorType);
    }
}
