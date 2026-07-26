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
 * @description : PlatformPayloadKeyMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface PlatformPayloadKeyMapper extends BaseMapper<PlatformPayloadKeyDO> {
}
