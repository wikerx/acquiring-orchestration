package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.MerchantResponseKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantResponseKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户响应加密公钥 MyBatisPlus Mapper
 * @status : create
 */

@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantResponseKeyMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : MerchantResponseKeyMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface MerchantResponseKeyMapper extends BaseMapper<MerchantResponseKeyDO> {
}
