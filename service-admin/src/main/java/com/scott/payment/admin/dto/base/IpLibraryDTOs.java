package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryDTOs
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : IP Library DTOs 聚合类型，位于 运营后台服务，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 * @status : create
 */
public final class IpLibraryDTOs {

    private IpLibraryDTOs() {
    }

    /**
     * IP 库分页查询请求。
     */
    @Data
    public static class IpLibraryQueryRequest {
        /**
         * page No，用于控制分页查询、批量扫描或任务单次处理规模。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private int pageNo = 1;
        /**
         * page Size，用于控制分页查询、批量扫描或任务单次处理规模。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private int pageSize = 10;
        /**
         * IP Type，用于区分 IP Library Query Request 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipType;
        /**
         * IP Address，用于保存 IP Library Query Request 中与 ipaddress 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipAddress;
        /**
         * 返回安全页码。
         */
        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        /**
         * 返回安全分页大小。
         */
        public int safePageSize() {
            if (pageSize <= 0) {
                return 10;
            }
            return Math.min(pageSize, 200);
        }
    }

    /**
     * IP 精确命中查询请求。
     */
    @Data
    public static class IpLibraryLookupRequest {
        @NotBlank(message = "ipAddress is required")
        /**
         * IP Address，用于保存 IP Library Lookup Request 中与 ipaddress 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipAddress;
        /**
         * IP Type，用于区分 IP Library Lookup Request 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipType;
    }

    /**
     * IP 库列表展示对象。
     */
    @Data
    public static class IpLibraryRecordResponse {
        /**
         * IP Library Record Response 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private Long id;
        /**
         * IP Type，用于区分 IP Library Record Response 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipType;
        /**
         * IP Number Start，用于保存 IP Library Record Response 中与 ipnumberstart 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipNumberStart;
        /**
         * IP Number End，用于保存 IP Library Record Response 中与 ipnumberend 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipNumberEnd;
        /**
         * IP Address Start，用于保存 IP Library Record Response 中与 ipaddressstart 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipAddressStart;
        /**
         * IP Address End，用于保存 IP Library Record Response 中与 ipaddressend 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String ipAddressEnd;
        /**
         * country Alpha 2，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String countryAlpha2;
        /**
         * country Alpha 3，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String countryAlpha3;
        /**
         * country Numeric，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String countryNumeric;
        /**
         * country Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String countryName;
        /**
         * state Province，用于保存 IP Library Record Response 中与 stateprovince 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String stateProvince;
        /**
         * city，用于保存 IP Library Record Response 中与 city 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String city;
        /**
         * data Version，用于保存 IP Library Record Response 中与 dataversion 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String dataVersion;
        /**
         * create Time，用于保存 IP Library Record Response 中与 createtime 相关的业务属性。
         * <p>
         * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * create By，用于保存 IP Library Record Response 中与 createby 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String createBy;
    }
}
