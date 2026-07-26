package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.PlatformPayloadKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PlatformPayloadKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 平台报文加密 RSA 密钥 MyBatisPlus Mapper
 * @status : create
 */

@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PlatformPayloadKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : Platform Payload Key Mapper 映射组件，位于 商户开放接口服务，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface PlatformPayloadKeyMapper extends BaseMapper<PlatformPayloadKeyDO> {
}
