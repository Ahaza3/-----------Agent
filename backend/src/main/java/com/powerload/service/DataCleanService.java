package com.powerload.service;

import com.powerload.entity.LoadData;

import java.util.List;

/**
 * 数据清洗服务 — 缺失值填充 + 异常值过滤
 */
public interface DataCleanService {

    /**
     * 缺失值填充：对 load_mw 使用前向填充 + 后向填充 + 线性插值
     *
     * @param data 原始负荷数据（按时间升序）
     * @return 填充后的数据（原地修改）
     */
    List<LoadData> fillMissing(List<LoadData> data);

    /**
     * 异常值过滤：3-sigma 法标记并修正离群点
     *
     * @param data 负荷数据列表
     * @return 过滤后的数据（异常值替换为均值）
     */
    List<LoadData> filterOutliers(List<LoadData> data);

    /**
     * 完整清洗流程：先填充缺失值，再过滤异常值
     *
     * @param data 原始数据
     * @return 清洗后数据
     */
    List<LoadData> clean(List<LoadData> data);
}
