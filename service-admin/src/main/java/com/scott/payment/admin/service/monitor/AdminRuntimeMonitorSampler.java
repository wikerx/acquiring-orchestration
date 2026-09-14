package com.scott.payment.admin.service.monitor;

import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.RuntimeSample;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRuntimeMonitorSampler
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : Admin JVM 运行时采样器，按分钟采集 CPU、内存、线程、GC、磁盘和运行时长，并在进程内保留七天有界历史。
 * @status : create
 */
@Component
public class AdminRuntimeMonitorSampler {

    /** 七天、每分钟一条采样对应的最大历史容量。 */
    static final int MAX_SAMPLES = 7 * 24 * 60;

    /** 运行时进程内历史，元素按采样时间升序保存。 */
    private final Deque<RuntimeSample> samples = new ArrayDeque<>(MAX_SAMPLES);

    /** 在 Spring Bean 初始化完成后采集首个 JVM 数据点。 */
    @PostConstruct
    public void initialize() {
        sample();
    }

    /** 每分钟采集一次当前 Admin JVM 运行指标。 */
    @Scheduled(fixedRate = 60_000L)
    public synchronized void sample() {
        addSample(capture());
    }

    synchronized void addSample(RuntimeSample sample) {
        samples.addLast(sample);
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
    }

    /**
     * 返回最近一次采样；历史为空时立即采集但不修改历史队列。
     *
     * @return 当前 JVM 运行时采样
     */
    public synchronized RuntimeSample current() {
        RuntimeSample latest = samples.peekLast();
        return latest == null ? capture() : latest;
    }

    /**
     * 查询闭区间内的进程内 JVM 采样。
     *
     * @param beginTime 查询开始时间，不允许为空
     * @param endTime 查询结束时间，不允许为空
     * @return 按采样时间升序排列的运行时数据
     */
    public synchronized List<RuntimeSample> between(LocalDateTime beginTime, LocalDateTime endTime) {
        List<RuntimeSample> result = new ArrayList<>();
        for (RuntimeSample sample : samples) {
            if (!sample.getTimestamp().isBefore(beginTime) && !sample.getTimestamp().isAfter(endTime)) {
                result.add(sample);
            }
        }
        return result;
    }

    /**
     * 通过 JVM MXBean 和本机文件系统采集单个运行时快照；平台不支持的指标保持空值。
     *
     * @return 当前 Admin JVM 与宿主机运行时快照
     */
    private RuntimeSample capture() {
        RuntimeSample sample = new RuntimeSample();
        sample.setTimestamp(LocalDateTime.now());

        java.lang.management.OperatingSystemMXBean baseOs = ManagementFactory.getOperatingSystemMXBean();
        sample.setSystemLoadAverage(nonNegativeFiniteOrNull(baseOs.getSystemLoadAverage()));
        if (baseOs instanceof com.sun.management.OperatingSystemMXBean os) {
            sample.setProcessCpuPercent(percentOrNull(os.getProcessCpuLoad()));
            sample.setSystemCpuPercent(percentOrNull(os.getCpuLoad()));
            setPhysicalMemory(sample, os.getTotalMemorySize(), os.getFreeMemorySize());
        }

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        sample.setHeapUsedBytes(heap.getUsed());
        sample.setHeapCommittedBytes(heap.getCommitted());
        sample.setHeapMaxBytes(nonNegativeOrNull(heap.getMax()));
        sample.setNonHeapUsedBytes(nonHeap.getUsed());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        sample.setThreadCount(threads.getThreadCount());
        sample.setPeakThreadCount(threads.getPeakThreadCount());
        sample.setDaemonThreadCount(threads.getDaemonThreadCount());

        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        sample.setLoadedClassCount(classes.getLoadedClassCount());

        long gcCount = 0L;
        long gcDuration = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(collector.getCollectionCount(), 0L);
            gcDuration += Math.max(collector.getCollectionTime(), 0L);
        }
        sample.setGcCount(gcCount);
        sample.setGcDurationMillis(gcDuration);

        File root = firstRoot();
        if (root != null) {
            setDiskSpace(sample, root.getTotalSpace(), root.getFreeSpace());
        }
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        sample.setUptimeMillis(runtime.getUptime());
        return sample;
    }

    /**
     * 选择 JVM 可见的首个文件系统根目录作为磁盘容量采样点。
     *
     * @return 首个根目录；运行环境未暴露根目录时返回 {@code null}
     */
    private File firstRoot() {
        File[] roots = File.listRoots();
        return roots == null || roots.length == 0 ? null : roots[0];
    }

    /**
     * 在总量和空闲量有效时回填物理内存指标。
     *
     * @param sample 待补充的运行时快照
     * @param totalBytes 物理内存总量，单位字节
     * @param freeBytes 可用物理内存，单位字节
     */
    private void setPhysicalMemory(RuntimeSample sample, long totalBytes, long freeBytes) {
        if (totalBytes <= 0L || freeBytes < 0L || freeBytes > totalBytes) {
            return;
        }
        sample.setPhysicalMemoryTotalBytes(totalBytes);
        sample.setPhysicalMemoryUsedBytes(totalBytes - freeBytes);
    }

    /**
     * 在总量和空闲量有效时回填磁盘容量指标。
     *
     * @param sample 待补充的运行时快照
     * @param totalBytes 磁盘总量，单位字节
     * @param freeBytes 磁盘可用量，单位字节
     */
    private void setDiskSpace(RuntimeSample sample, long totalBytes, long freeBytes) {
        if (totalBytes <= 0L || freeBytes < 0L || freeBytes > totalBytes) {
            return;
        }
        sample.setDiskTotalBytes(totalBytes);
        sample.setDiskUsedBytes(totalBytes - freeBytes);
    }

    /**
     * 将零到一之间的 CPU 比率转换为保留两位小数的百分比。
     *
     * @param value JVM MXBean 返回的 CPU 比率
     * @return 百分比；负数或非有限值返回 {@code null}
     */
    static Double percentOrNull(double value) {
        return value < 0D || !Double.isFinite(value)
                ? null
                : Math.round(value * 10_000D) / 100D;
    }

    /**
     * 过滤不受支持的负数和非有限浮点指标。
     *
     * @param value 原始指标
     * @return 非负有限值或 {@code null}
     */
    static Double nonNegativeFiniteOrNull(double value) {
        return value < 0D || !Double.isFinite(value) ? null : value;
    }

    /**
     * 将 JVM MXBean 使用的负数“不可用”值转换为空值。
     *
     * @param value 原始长整型指标
     * @return 非负值或 {@code null}
     */
    static Long nonNegativeOrNull(long value) {
        return value < 0L ? null : value;
    }
}
