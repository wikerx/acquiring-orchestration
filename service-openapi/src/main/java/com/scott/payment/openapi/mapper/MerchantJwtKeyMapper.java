package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.MerchantJwtKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantJwtKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户 JWT 签名密钥 MyBatisPlus Mapper
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantJwtKeyMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIMerchant Jwt Key 数据访问 Mapper，位于 service-openapi 的数据访问层，用于定义调用契约和职责边界。
 * @status : create
 */
@Mapper
public interface MerchantJwtKeyMapper extends BaseMapper<MerchantJwtKeyDO> {
}
