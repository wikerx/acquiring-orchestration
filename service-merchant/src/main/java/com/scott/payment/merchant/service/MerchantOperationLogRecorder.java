package com.scott.payment.merchant.service;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import com.scott.payment.component.web.operation.service.OperationLogRecorder;
import com.scott.payment.merchant.config.MerchantOperationLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogRecorder
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志上报记录器
 * @status : create
 */
@Slf4j
@Component
public class MerchantOperationLogRecorder implements OperationLogRecorder {

    /**
     * 默认连接超时时间。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 默认读取超时时间。
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 商户管理系统操作日志配置。
     */
    private final MerchantOperationLogProperties properties;

    /**
     * HTTP 客户端。
     */
    private final RestTemplate restTemplate;

    /**
     * 创建商户管理系统操作日志上报记录器。
     *
     * @param properties          操作日志配置
     * @param restTemplateBuilder HTTP 客户端构建器
     */
    public MerchantOperationLogRecorder(MerchantOperationLogProperties properties,
                                        RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .build();
    }

    /**
     * 将商户管理系统操作日志上报到 service-admin。
     * <p>
     * 上报失败只打印警告，不影响商户管理端业务接口返回。
     *
     * @param record 操作日志采集记录
     */
    @Override
    public void record(OperationLogRecord record) {
        if (!properties.isEnabled() || record == null) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(
                    properties.getAdminRecordUrl(),
                    new HttpEntity<>(JsonUtils.toJsonString(record), headers),
                    String.class
            );
        } catch (RestClientException exception) {
            log.warn("商户管理系统操作日志上报失败，目标地址：{}，原因：{}",
                    properties.getAdminRecordUrl(),
                    exception.getMessage());
        }
    }
}
