package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.PlatformPayloadKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 平台报文加密 RSA 密钥 MyBatisPlus Mapper
 */

@Mapper
public interface PlatformPayloadKeyMapper extends BaseMapper<PlatformPayloadKeyDO> {
}
