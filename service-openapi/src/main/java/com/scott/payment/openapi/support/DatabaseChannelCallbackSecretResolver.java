package com.scott.payment.openapi.support;

import com.scott.payment.openapi.mapper.ChannelCallbackSecretMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DatabaseChannelCallbackSecretResolver
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : 从管理端 MID 元数据读取渠道回调密钥的默认实现；数据库异常只返回未解析结果，由安全组件继续执行配置兜底，不泄露异常正文或密钥。
 * @status : create
 */
@Component
public class DatabaseChannelCallbackSecretResolver implements ChannelCallbackSecretResolver {

    private final ChannelCallbackSecretMapper mapper;

    /**
     * 创建数据库回调密钥解析器。
     *
     * @param mapper 回调密钥只读 Mapper
     */
    public DatabaseChannelCallbackSecretResolver(ChannelCallbackSecretMapper mapper) {
        this.mapper = mapper;
    }

    /** 从启用且处于有效期内的 MID 配置解析回调密钥。 */
    @Override
    public String resolve(String channelCode, String merchantId) {
        if (!StringUtils.hasText(channelCode) || !StringUtils.hasText(merchantId)) {
            return null;
        }
        try {
            return mapper.findCallbackSecret(channelCode.trim().toUpperCase(Locale.ROOT), merchantId.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
