package com.powerload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerload.entity.TicketFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketFeedbackMapper extends BaseMapper<TicketFeedback> {
    @Select("SELECT * FROM ticket_feedback WHERE ticket_id = #{ticketId} LIMIT 1")
    TicketFeedback selectByTicketId(@Param("ticketId") Long ticketId);
}
