package com.powerload.dto.response;

import lombok.Data;

/**
 * 实时负荷数据点（秒级）
 *
 * <p>独立于 {@link com.powerload.entity.LoadData}（小时级历史数据），
 * 通过 WebSocket /topic/load 推送，前端按 timestamp/sequence 追加形成连续曲线。</p>
 *
 * <p>所有时间字段统一使用 epoch 毫秒，浏览器无需做时区解析。</p>
 */
@Data
public class RealtimeLoadPoint {

    /** 数据生成时间（epoch 毫秒） */
    private long timestamp;

    /** 单调递增序号，用于去重和排序 */
    private long sequence;

    /** 实时负荷 (MW) */
    private float loadMw;

    /** 温度 (°C)，可为 null */
    private Float temperature;

    /** 湿度 (%)，可为 null */
    private Float humidity;

    /** 数据来源：MOCK / METER / 等 */
    private String source;
}
