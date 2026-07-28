package com.nageoffer.ai.tinyagent.react.multiagent;

/**
 * Agent 之间的显式消息。消息是事件，不承担请求级状态存储
 */
public record AgentMessage(
        long id,
        String from,
        String to,
        Type type,
        String content) {

    public enum Type {
        /**
         * 编排者派给某个领域 Agent 的一次性子任务
         */
        TASK,
        /**
         * 领域 Agent 已把可用结果写进黑板，通知聚合器来取
         */
        RESULT_READY,
        /**
         * 领域 Agent 没能正常完成。它同样是一个要投递的事实，
         * 不该退化成异常——聚合器需要据此生成降级终稿，而不是崩掉
         */
        RESULT_FAILED
    }

    public AgentMessage {
        if (id < 0) {
            throw new IllegalArgumentException("消息 ID 不能为负数");
        }
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("消息发送者和接收者不能为空");
        }
        from = from.strip();
        to = to.strip();
        type = type == null ? Type.TASK : type;
        content = content == null ? "" : content.strip();
    }
}
