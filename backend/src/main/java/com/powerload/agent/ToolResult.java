package com.powerload.agent;

import lombok.Data;

import java.util.Map;
/**
 * 工具执行结果 — 由 Tool.execute() 返回，统一数据结构。
 *
 * <p>data 为工具返回的结构化数据（Map/List/基本类型），
 * chart 为可选的 ECharts 配置，仅 QueryLoadTool 在需要图表时填充。</p>
 */
@Data
public class ToolResult {

    /** 执行是否成功 */
    private boolean success;

    /** 可读消息（成功时为摘要，失败时为错误原因） */
    private String message;

    /** 结构化业务数据 */
    private Object data;

    /** ECharts 图表配置（可选） */
    private Object chart;

    /** 数据来源、时间范围、模型版本等可信度信息 */
    private Map<String, Object> provenance;

    public static ToolResult ok(String message, Object data) {
        ToolResult r = new ToolResult();
        r.success = true;
        r.message = message;
        r.data = data;
        return r;
    }

    public static ToolResult fail(String message) {
        ToolResult r = new ToolResult();
        r.success = false;
        r.message = message;
        r.data = null;
        return r;
    }
}
