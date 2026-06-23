package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户部门数据库实体。
 */
@Data
@TableName("sys_merchant_dept")
public class SysMerchantDeptDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private Long parentId;
    private String deptCode;
    private String deptName;
    private Long leaderAccountId;
    private String phone;
    private String email;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Long deleted;
}
