package com.powerload.service;

import com.powerload.dto.response.RealtimeLoadPoint;
import com.powerload.dto.response.RealtimeLoadStatus;

import java.util.List;

/**
 * 实时负荷服务
 *
 * <p>管理秒级实时数据的内存环形缓存，提供有状态模拟器和查询能力。
 * 不写入 MySQL，仅用于 WebSocket 推送和告警检测。</p>
 */
public interface RealtimeLoadService {

    /**
     * 初始化模拟器状态（从小时级 DB 数据获取初始值）
     *
     * @param initialLoad 初始负荷 (MW)
     * @param initialTemp 初始温度 (°C)
     * @param initialHum  初始湿度 (%)
     */
    void initialize(float initialLoad, float initialTemp, float initialHum);

    /**
     * 生成下一个实时点并追加到环形缓存
     *
     * <p>使用均值回归随机游走：缓慢靠近目标负荷，单秒变化 ≤ 3 MW。</p>
     *
     * @return 新生成的实时数据点
     */
    RealtimeLoadPoint generateAndAppend();

    /** 接收外部或模拟遥测点；重复返回 null，BAD 点只审计持久化且不进入主曲线。 */
    RealtimeLoadPoint accept(RealtimeLoadPoint point);

    /** 更新正常模式的小时级目标负荷。 */
    void setTargetLoad(float targetLoad);

    /** 立即回到正常基线，用于演示开始前复位。 */
    RealtimeLoadStatus enterNormalMode();

    /** 启动受控负荷突增场景。 */
    RealtimeLoadStatus startSpikeDemo();

    /** 从当前负荷平滑恢复到正常基线。 */
    RealtimeLoadStatus startRecoveryDemo();

    /** 获取当前实时模拟器和演示场景状态。 */
    RealtimeLoadStatus getStatus();

    /**
     * 获取最新一个实时点
     *
     * @return 最新点，无数据时返回 null
     */
    RealtimeLoadPoint getLatest();

    /**
     * Latest newly received GOOD point for alert evaluation. Recovered, late,
     * estimated and bad points are intentionally excluded from this input.
     */
    RealtimeLoadPoint getLatestForAlert();

    /**
     * 查询最近 N 分钟内的实时数据点（返回防御性副本）
     *
     * @param minutes 向前查询的分钟数，建议 1~60
     * @return 按 timestamp 升序排列的实时点列表
     */
    List<RealtimeLoadPoint> getRecent(int minutes);

    /**
     * 获取当前缓存大小
     */
    int size();
}
