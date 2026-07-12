package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantAccountDeptMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Merchant Account Dept 数据访问 Mapper，位于 component-library/component-db 的数据访问层，用于定义调用契约和职责边界。
 * @status : create
 */
@Mapper
public interface SysMerchantAccountDeptMapper extends BaseMapper<SysMerchantAccountDeptDO> {
}
