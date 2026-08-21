package com.scott.payment.component.db.email.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.email.entity.SharedEmailTemplateDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SharedEmailTemplateMapper
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共组件数据访问层的邮件模板只读 Mapper，不承载模板维护或缓存失效逻辑
 * @status : create
 */
public interface SharedEmailTemplateMapper extends BaseMapper<SharedEmailTemplateDO> {
}
