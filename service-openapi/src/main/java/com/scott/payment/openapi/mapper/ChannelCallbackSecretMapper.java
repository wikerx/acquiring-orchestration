package com.scott.payment.openapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackSecretMapper
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : 回调验签只读 Mapper，仅从 channel_mid_config 提取指定 MID 的 callbackSecret JSON 字段，不返回整段敏感元数据。
 * @status : create
 */
@Mapper
public interface ChannelCallbackSecretMapper {

    /**
     * 查询指定渠道 MID 的回调签名密钥。
     *
     * @param channelCode 渠道编码
     * @param merchantId 回调 Header 中的 MID
     * @return callbackSecret；不存在时返回 {@code null}
     */
    @Select("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata_value_json, '$.callbackSecret'))
            FROM channel_mid_config
            WHERE channel_code = #{channelCode}
              AND channel_mid = #{merchantId}
              AND mid_status = 1
              AND deleted = 0
              AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
              AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
            ORDER BY id DESC
            LIMIT 1
            """)
    String findCallbackSecret(@Param("channelCode") String channelCode,
                              @Param("merchantId") String merchantId);
}
