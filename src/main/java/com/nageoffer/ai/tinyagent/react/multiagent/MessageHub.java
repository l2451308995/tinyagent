package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求内消息总线：按接收者维护邮箱
 * <p>
 * MessageHub 负责传递任务和完成事件；需要长期读取的用户请求、共享约束和 Agent 结果
 * 由 {@link AgenticScope} 保存。把事件流和状态分开后，两者的生命周期不会混在一起
 */
public final class MessageHub {

    private final Map<String, ArrayDeque<AgentMessage>> inboxes =
            new LinkedHashMap<>();
    private long nextId = 1;

    public synchronized void send(
            String from,
            String to,
            AgentMessage.Type type,
            String content) {
        AgentMessage message =
                new AgentMessage(nextId++, from, to, type, content);
        inboxes.computeIfAbsent(message.to(), ignored -> new ArrayDeque<>())
                .addLast(message);
    }

    /**
     * 一次取走某个接收者的全部待处理消息
     */
    public synchronized List<AgentMessage> drain(String recipient) {
        ArrayDeque<AgentMessage> inbox = inboxes.remove(recipient);
        if (inbox == null || inbox.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(inbox));
    }

    public synchronized int pending(String recipient) {
        ArrayDeque<AgentMessage> inbox = inboxes.get(recipient);
        return inbox == null ? 0 : inbox.size();
    }
}
