package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailSendRecordMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户邮件发送记录 Mapper，位于 service-merchant 数据访问层；记录商户 MFA 通知发送结果供管理系统邮件记录页面查询。
 * @status : create
 */
@Mapper
public interface MerchantEmailSendRecordMapper extends BaseMapper<MerchantEmailSendRecordDO> {
}
