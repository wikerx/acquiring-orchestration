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
 * @description : MerchantJwtKeyMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface MerchantJwtKeyMapper extends BaseMapper<MerchantJwtKeyDO> {
}
