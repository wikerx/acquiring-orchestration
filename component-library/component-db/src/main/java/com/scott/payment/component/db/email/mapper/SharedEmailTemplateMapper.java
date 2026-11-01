package com.scott.payment.component.db.email.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.email.entity.SharedEmailTemplateDO;

/**
 * 公共组件数据访问层的邮件模板只读 Mapper，不承载模板维护或缓存失效逻辑
 */
public interface SharedEmailTemplateMapper extends BaseMapper<SharedEmailTemplateDO> {
}
