package com.powerload.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerload.common.GridTopologyConstants;
import com.powerload.dto.response.LoadStats;
import com.powerload.entity.LoadData;
import com.powerload.mapper.LoadDataMapper;
import com.powerload.service.LoadDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * 负荷数据查询服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadDataServiceImpl implements LoadDataService {

    private final LoadDataMapper loadDataMapper;

    @Override
    public List<LoadData> queryRange(LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
               .ge(LoadData::getTime, start)
               .lt(LoadData::getTime, end)
               .orderByAsc(LoadData::getTime);
        return loadDataMapper.selectList(wrapper).stream()
                .filter(data -> data.getTime() != null
                        && data.getTime().getMinute() == 0
                        && data.getTime().getSecond() == 0)
                .toList();
    }

    @Override
    public LoadData getLatest() {
        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
               .orderByDesc(LoadData::getTime)
               .last("LIMIT 1");
        return loadDataMapper.selectOne(wrapper);
    }

    @Override
    public LoadData getLatestHourly() {
        LambdaQueryWrapper<LoadData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoadData::getNodeId, GridTopologyConstants.ROOT_NODE_ID)
               .apply("MINUTE(time) = 0 AND SECOND(time) = 0")
               .orderByDesc(LoadData::getTime)
               .last("LIMIT 1");
        return loadDataMapper.selectOne(wrapper);
    }

    @Override
    public LoadStats getStats(LocalDateTime start, LocalDateTime end) {
        List<LoadData> list = queryRange(start, end);

        if (list.isEmpty()) {
            LoadStats empty = new LoadStats();
            empty.setDataPoints(0);
            return empty;
        }

        DoubleSummaryStatistics stats = list.stream()
                .mapToDouble(d -> d.getLoadMw() != null ? d.getLoadMw() : 0.0)
                .summaryStatistics();

        LoadData peak = list.stream()
                .max((a, b) -> Float.compare(
                        a.getLoadMw() != null ? a.getLoadMw() : 0f,
                        b.getLoadMw() != null ? b.getLoadMw() : 0f))
                .orElse(null);

        LoadData valley = list.stream()
                .min((a, b) -> Float.compare(
                        a.getLoadMw() != null ? a.getLoadMw() : 0f,
                        b.getLoadMw() != null ? b.getLoadMw() : 0f))
                .orElse(null);

        double avg = stats.getAverage();
        double variance = list.stream()
                .mapToDouble(d -> {
                    double v = d.getLoadMw() != null ? d.getLoadMw() : 0.0;
                    return Math.pow(v - avg, 2);
                })
                .average()
                .orElse(0.0);

        LoadStats result = new LoadStats();
        result.setPeakLoad((float) stats.getMax());
        result.setPeakTime(peak != null ? peak.getTime() : null);
        result.setValleyLoad((float) stats.getMin());
        result.setValleyTime(valley != null ? valley.getTime() : null);
        result.setAvgLoad((float) avg);
        result.setLoadRate(stats.getMax() > 0 ? (float) (avg / stats.getMax()) : 0f);
        result.setStdDeviation((float) Math.sqrt(variance));
        result.setDataPoints(list.size());

        log.debug("统计完成: {} 条数据, 峰值={}MW, 谷值={}MW, 均值={}MW",
                list.size(), stats.getMax(), stats.getMin(), avg);

        return result;
    }
}
