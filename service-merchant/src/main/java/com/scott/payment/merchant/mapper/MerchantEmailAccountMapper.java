package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailAccountMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户邮件发件账户 Mapper，位于 service-merchant 数据访问层；只读取管理系统维护的发件账户配置用于商户安全通知发送。
 * @status : create
 */
@Mapper
public interface MerchantEmailAccountMapper extends BaseMapper<MerchantEmailAccountDO> {
}
