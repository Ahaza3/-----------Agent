package com.powerload.controller;

import com.powerload.common.R;
import com.powerload.dto.response.ConversationMessageResponse;
import com.powerload.dto.response.ConversationSummary;
import com.powerload.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agent 历史会话查询与删除接口。 */
@RestController
@RequestMapping("/api/v1/agent/conversations")
@RequiredArgsConstructor
public class AgentConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public R<List<ConversationSummary>> list(
            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(conversationService.listConversations(limit));
    }

    @GetMapping("/{conversationId}")
    public R<List<ConversationMessageResponse>> detail(
            @PathVariable String conversationId) {
        return R.ok(conversationService.loadConversation(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public R<Void> delete(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return R.ok();
    }
}
