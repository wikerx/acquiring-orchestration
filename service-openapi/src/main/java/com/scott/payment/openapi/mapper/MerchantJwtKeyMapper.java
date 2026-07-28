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

@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantJwtKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : Merchant JWT Key Mapper 映射组件，位于 商户开放接口服务，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface MerchantJwtKeyMapper extends BaseMapper<MerchantJwtKeyDO> {
}
