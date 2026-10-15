package com.scott.payment.component.db.reference.entity;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinIndexSourceSignatureDO
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Card BIN 从库快照源签名投影，用行数和稳定校验值识别构建期间的数据变化
 * @status : create
 */
@Data
public class CardBinIndexSourceSignatureDO {

    /** 参与快照构建的启用且未逻辑删除记录数。 */
    private Long rowCount;

    /** 对查询相关字段计算的无序 CRC 聚合值。 */
    private Long checksum;
}
