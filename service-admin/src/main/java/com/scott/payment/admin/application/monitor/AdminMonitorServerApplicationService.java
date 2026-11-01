package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.dto.monitor.ServerMonitorResponse;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.CpuInfo;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.DiskInfo;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.JvmInfo;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.RuntimeInfo;
import com.scott.payment.admin.dto.monitor.ServerMonitorResponse.SystemInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 管理端服务器监控应用服务，采集当前 Admin 节点 JVM、操作系统、磁盘和运行时信息并组装脱敏响应。
 *
 * <p>负责管理后台服务器监控用例编排，采集当前节点的 CPU、JVM、系统与磁盘等运行指标，
 * 并组装为前端监控页面可直接消费的结构化数据。</p>
 */
@Service
public class AdminMonitorServerApplicationService {

    /**
     * 采集当前节点的 CPU、JVM、系统和磁盘运行信息。
     *
     * @return 服务器监控摘要
     */
    public ServerMonitorResponse serverInfo() {
        ServerMonitorResponse response = new ServerMonitorResponse();

        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        CpuInfo cpu = new CpuInfo();
        cpu.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
        cpu.setSystemLoadAverage(round(operatingSystemMXBean.getSystemLoadAverage(), 2));
        cpu.setArch(operatingSystemMXBean.getArch());
        cpu.setName(operatingSystemMXBean.getName());
        cpu.setVersion(operatingSystemMXBean.getVersion());
        response.setCpu(cpu);

        Runtime runtime = Runtime.getRuntime();
        JvmInfo jvm = new JvmInfo();
        jvm.setMax(bytesToMB(runtime.maxMemory()) + " MB");
        jvm.setTotal(bytesToMB(runtime.totalMemory()) + " MB");
        jvm.setFree(bytesToMB(runtime.freeMemory()) + " MB");
        jvm.setUsed(bytesToMB(runtime.totalMemory() - runtime.freeMemory()) + " MB");
        jvm.setJavaVersion(System.getProperty("java.version"));
        jvm.setJavaHome(System.getProperty("java.home"));
        response.setJvm(jvm);

        SystemInfo system = new SystemInfo();
        system.setOsName(System.getProperty("os.name"));
        system.setOsArch(System.getProperty("os.arch"));
        system.setUserName(System.getProperty("user.name"));
        response.setSystem(system);

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        RuntimeInfo runtimeInfo = new RuntimeInfo();
        runtimeInfo.setUptime(formatUptime(runtimeMXBean.getUptime()));
        runtimeInfo.setStartTime(runtimeMXBean.getStartTime());
        response.setRuntime(runtimeInfo);

        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0) {
            File root = roots[0];
            DiskInfo disk = new DiskInfo();
            disk.setPath(root.getAbsolutePath());
            disk.setTotal(bytesToGB(root.getTotalSpace()) + " GB");
            disk.setFree(bytesToGB(root.getFreeSpace()) + " GB");
            disk.setUsed(bytesToGB(root.getTotalSpace() - root.getFreeSpace()) + " GB");
            disk.setUsagePercent(usagePercent(root));
            response.setDisk(disk);
        }
        return response;
    }

    /**
     * 将字节数转换为 MB，供监控展示使用。
     *
     * @param bytes 字节数
     * @return MB
     */
    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * 将字节数转换为 GB，供磁盘展示使用。
     *
     * @param bytes 字节数
     * @return GB
     */
    private long bytesToGB(long bytes) {
        return bytes / (1024 * 1024 * 1024);
    }

    /**
     * 对监控数值做四舍五入，避免原始浮点值不利于前端展示。
     *
     * @param value  原始值
     * @param places 保留位数
     * @return 舍入结果
     */
    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 把毫秒级运行时长转换为易读文本。
     *
     * @param milliseconds 运行时长
     * @return 文本化时长
     */
    private String formatUptime(long milliseconds) {
        long days = milliseconds / (24 * 60 * 60 * 1000);
        long hours = (milliseconds % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);
        return days + "天 " + hours + "小时 " + minutes + "分钟";
    }

    /**
     * 计算磁盘使用率。
     *
     * @param root 磁盘根目录
     * @return 百分比文本
     */
    private String usagePercent(File root) {
        long total = root.getTotalSpace();
        if (total == 0) {
            return "N/A";
        }
        long used = total - root.getFreeSpace();
        return BigDecimal.valueOf(used * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP) + "%";
    }
}
