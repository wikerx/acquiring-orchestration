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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantResponseKeyMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIMerchant Response Key 数据访问 Mapper，位于 service-openapi 的数据访问层，用于定义调用契约和职责边界。
 * @status : create
 */
@Mapper
public interface MerchantResponseKeyMapper extends BaseMapper<MerchantResponseKeyDO> {
}
