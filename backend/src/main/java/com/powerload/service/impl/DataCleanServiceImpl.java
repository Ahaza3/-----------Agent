package com.powerload.service.impl;

import com.powerload.entity.LoadData;
import com.powerload.service.DataCleanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据清洗服务实现
 *
 * <p>清洗流程：缺失值填充（前向+后向+线性插值） → 异常值过滤（3-sigma 修正）。</p>
 */
@Slf4j
@Service
public class DataCleanServiceImpl implements DataCleanService {

    /** 3-sigma 阈值（μ ± k*σ 之外视为异常） */
    private static final double SIGMA_FACTOR = 3.0;

    @Override
    public List<LoadData> fillMissing(List<LoadData> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        int filledCount = 0;

        // 1. 前向填充：用前一个有效值替换 null
        Float lastValid = null;
        for (LoadData d : data) {
            if (d.getLoadMw() == null) {
                if (lastValid != null) {
                    d.setLoadMw(lastValid);
                    filledCount++;
                }
            } else {
                lastValid = d.getLoadMw();
            }
        }

        // 2. 后向填充：处理开头仍为 null 的值
        Float nextValid = null;
        for (int i = data.size() - 1; i >= 0; i--) {
            LoadData d = data.get(i);
            if (d.getLoadMw() == null) {
                if (nextValid != null) {
                    d.setLoadMw(nextValid);
                    filledCount++;
                }
            } else {
                nextValid = d.getLoadMw();
            }
        }

        // 3. 线性插值：对仍然有 null 的中间缺口做插值
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getLoadMw() != null) {
                continue;
            }
            // 找到前面最近的已知值
            Float before = null;
            int beforeIdx = -1;
            for (int j = i - 1; j >= 0; j--) {
                if (data.get(j).getLoadMw() != null) {
                    before = data.get(j).getLoadMw();
                    beforeIdx = j;
                    break;
                }
            }
            // 找到后面最近的已知值
            Float after = null;
            int afterIdx = -1;
            for (int j = i + 1; j < data.size(); j++) {
                if (data.get(j).getLoadMw() != null) {
                    after = data.get(j).getLoadMw();
                    afterIdx = j;
                    break;
                }
            }
            if (before != null && after != null) {
                float ratio = (float) (i - beforeIdx) / (afterIdx - beforeIdx);
                data.get(i).setLoadMw(before + ratio * (after - before));
                filledCount++;
            }
        }

        if (filledCount > 0) {
            log.info("缺失值填充完成: {} 个值被填充", filledCount);
        }
        return data;
    }

    @Override
    public List<LoadData> filterOutliers(List<LoadData> data) {
        if (data == null || data.size() < 3) {
            return data;
        }

        // 计算 μ 和 σ
        double sum = 0;
        int validCount = 0;
        for (LoadData d : data) {
            if (d.getLoadMw() != null) {
                sum += d.getLoadMw();
                validCount++;
            }
        }
        if (validCount == 0) {
            return data;
        }

        double mean = sum / validCount;
        double variance = 0;
        for (LoadData d : data) {
            if (d.getLoadMw() != null) {
                variance += Math.pow(d.getLoadMw() - mean, 2);
            }
        }
        double std = Math.sqrt(variance / validCount);

        double lower = mean - SIGMA_FACTOR * std;
        double upper = mean + SIGMA_FACTOR * std;

        int outlierCount = 0;
        for (LoadData d : data) {
            if (d.getLoadMw() == null) {
                continue;
            }
            float val = d.getLoadMw();
            if (val < lower || val > upper) {
                // 异常值替换为均值
                d.setLoadMw((float) mean);
                outlierCount++;
            }
        }

        if (outlierCount > 0) {
            log.info("异常值过滤完成: {} 个离群点（范围 [{:.1f}, {:.1f}]，μ={:.1f}，σ={:.1f}）",
                    outlierCount, lower, upper, mean, std);
        }
        return data;
    }

    @Override
    public List<LoadData> clean(List<LoadData> data) {
        fillMissing(data);
        filterOutliers(data);
        return data;
    }
}
