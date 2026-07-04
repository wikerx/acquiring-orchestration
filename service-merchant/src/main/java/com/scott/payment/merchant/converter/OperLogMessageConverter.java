package com.scott.payment.merchant.converter;

import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperLogMessageConverter
 * @date : 2026-06-20 10:31
 * @email : scott_x@163.com
 * @description : 操作日志 MQ 消息转商户落库请求转换器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperLogMessageConverter
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Oper Log Message Converter，位于 service-merchant 的对象转换层，用于定义调用契约和职责边界。
 * @status : create
 */
@Mapper
public interface OperLogMessageConverter {

    /**
     * 转换器单例。
     */
    OperLogMessageConverter INSTANCE = Mappers.getMapper(OperLogMessageConverter.class);

    /**
     * 将操作日志 MQ 消息转换为商户端落库请求。
     *
     * @param message 操作日志 MQ 消息
     * @return 商户端落库请求
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
