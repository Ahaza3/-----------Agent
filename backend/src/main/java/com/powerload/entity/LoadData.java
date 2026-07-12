package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("load_data")
public class LoadData {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据时间点（精确到小时） */
    private LocalDateTime time;


    private Integer month;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
