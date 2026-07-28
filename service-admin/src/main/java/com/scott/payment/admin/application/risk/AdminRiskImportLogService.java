package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.mapper.RiskManagementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskImportLogService
 * @date : 2026-07-10 00:00
 * @email : scott_x@163.com
 * @description : 风控导入批次日志应用服务，使用独立事务保留导入失败明细，避免业务数据回滚时丢失排查依据。
 * @status : create
 */
@Service
public class AdminRiskImportLogService {

    /**
     * risk Management Mapper 依赖，用于 Admin Risk Import Log Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final RiskManagementMapper riskManagementMapper;

    /**
     * 创建风控导入日志应用服务。
     *
     * @param riskManagementMapper 风控管理数据访问接口
     */
    public AdminRiskImportLogService(RiskManagementMapper riskManagementMapper) {
        this.riskManagementMapper = riskManagementMapper;
    }

    /**
     * 创建导入批次记录。
     *
     * @param moduleType   模块类型
     * @param functionCode 功能编码
     * @param batchNo      导入批次号
     * @param fileName     导入文件名
     * @param totalCount   CSV 数据行数
     * @param operator     操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createBatch(String moduleType, String functionCode, String batchNo, String fileName, int totalCount, String operator) {
        riskManagementMapper.insertImportBatch(moduleType, functionCode, batchNo, fileName, totalCount, operator);
    }

    /**
     * 标记导入批次成功。
     *
     * @param batchNo      导入批次号
     * @param successCount 成功导入行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(String batchNo, int successCount) {
        riskManagementMapper.updateImportBatch(batchNo, successCount, 0, 1, "导入成功");
    }

    /**
     * 标记导入批次失败并保留行级错误。当前导入策略为任一行失败则整体回滚，因此成功数固定记录为 0。
     *
     * @param batchNo     导入批次号
     * @param failedCount 失败行数
     * @param errors      已脱敏的行级错误
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String batchNo, int failedCount, java.util.List<ImportRowError> errors) {
        riskManagementMapper.updateImportBatch(batchNo, 0, failedCount, 3, "导入失败，业务数据已整体回滚");
        for (ImportRowError error : errors) {
            riskManagementMapper.insertImportError(batchNo, error.rowNo(), error.rawContent(), error.errorMessage());
        }
    }

    /**
     * 导入行级错误记录。rawContent 必须由调用方脱敏后再传入。
     */
    public record ImportRowError(int rowNo, String rawContent, String errorMessage) {
    }
}
