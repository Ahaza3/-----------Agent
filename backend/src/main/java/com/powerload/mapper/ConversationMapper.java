package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话记录 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
