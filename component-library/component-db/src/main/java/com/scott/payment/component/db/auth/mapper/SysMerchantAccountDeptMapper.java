package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantAccountDeptMapper
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : SysMerchantAccountDeptMapper MyBatis 数据访问接口，用于映射数据库表读写语句和领域查询条件，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public interface SysMerchantAccountDeptMapper extends BaseMapper<SysMerchantAccountDeptDO> {
}
