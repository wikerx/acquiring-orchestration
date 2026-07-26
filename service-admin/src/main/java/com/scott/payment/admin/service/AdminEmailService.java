package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountSaveRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailAccountTestRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplatePreviewResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateQuery;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailTemplateSaveRequest;
import com.scott.payment.component.core.model.PageResult;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailService
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : Admin Email Service 服务契约，位于 运营后台服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public interface AdminEmailService {

    /**
     * 查询账号，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<EmailAccountResponse> pageAccounts(EmailAccountQuery query);

    /**
     * 查询账号，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    EmailAccountResponse getAccount(Long id);

    /**
     * 创建账号，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    EmailAccountResponse createAccount(EmailAccountSaveRequest request);

    /**
     * 更新账号，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    EmailAccountResponse updateAccount(Long id, EmailAccountSaveRequest request);

    /**
     * 更新账号状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    EmailAccountResponse updateAccountStatus(Long id, Integer status);

    /**
     * 写入set默认账号，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailAccountResponse setDefaultAccount(Long id);

    /**
     * 删除或停用账号，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteAccount(Long id);

    /**
     * 发送testemail消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已确定 运营后台服务 的目标地址、消息主题、业务编号和重试策略。
     * 该方法可能调用外部系统、内部服务或 MQ；traceId 必须沿调用链透传，重试应保留原业务标识。
     * 异常边界：网络异常、超时或投递失败需转换为当前模块可识别的失败结果并记录脱敏摘要。
     * </p>
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailSendResult sendTestEmail(Long accountId, EmailAccountTestRequest request);

    /**
     * 查询模板，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<EmailTemplateResponse> pageTemplates(EmailTemplateQuery query);

    /**
     * 查询模板，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    EmailTemplateResponse getTemplate(Long id);

    /**
     * 创建模板，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    EmailTemplateResponse createTemplate(EmailTemplateSaveRequest request);

    /**
     * 更新模板，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 写入、更新或删除后的处理结果
     */
    EmailTemplateResponse updateTemplate(Long id, EmailTemplateSaveRequest request);

    /**
     * 构造模板对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailTemplateResponse copyTemplate(Long id);

    /**
     * 更新template状态，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在且当前状态允许变更。
     * 该方法可能更新状态、配置或审计时间；调用方需关注返回值或受影响行数判断是否真正生效。
     * 异常边界：状态冲突、版本冲突或持久化失败按当前模块异常规范返回。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 写入、更新或删除后的处理结果
     */
    EmailTemplateResponse updateTemplateStatus(Long id, Integer status);

    /**
     * 删除或停用模板，调用方需保证权限和状态允许该操作。
     * <p>
     * 前置条件：调用方已确认 运营后台服务 中目标记录存在、权限满足且状态允许删除或停用。
     * 该方法通常执行软删除、停用或批量标记；幂等结果以记录状态或受影响行数为准。
     * 异常边界：记录不存在、状态禁止删除或数据库更新失败会阻断后续流程。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     */
    void deleteTemplate(Long id);

    /**
     * 整理模板，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailTemplatePreviewResponse previewTemplate(EmailTemplatePreviewRequest request);

    /**
     * 查询记录，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    PageResult<EmailRecordResponse> pageRecords(EmailRecordQuery query);

    /**
     * 查询记录，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 查询得到的业务对象、分页结果或空结果
     */
    EmailRecordResponse getRecord(Long id);

    /**
     * 发送bytemplate消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已确定 运营后台服务 的目标地址、消息主题、业务编号和重试策略。
     * 该方法可能调用外部系统、内部服务或 MQ；traceId 必须沿调用链透传，重试应保留原业务标识。
     * 异常边界：网络异常、超时或投递失败需转换为当前模块可识别的失败结果并记录脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailSendResult sendByTemplate(EmailSendRequest request);

    /**
     * 发送resend消息或请求，补齐目标地址、链路标识和业务载荷。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    EmailSendResult resend(Long id);
}
