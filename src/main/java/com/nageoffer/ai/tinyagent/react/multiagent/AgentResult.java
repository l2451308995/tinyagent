package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子 Agent 可以交接的最小结果
 * <p>
 * summary 用来展示，facts 用来传递显式字段。通信层只运输结果，不负责证明模型生成内容正确；
 * 生产代码应当只把工具返回或业务系统确认过的字段写入 facts
 */
public record AgentResult(
        String agentName,
        Status status,
        String summary,
        Map<String, String> facts) {

    public enum Status {
        COMPLETED,
        FAILED
    }

    public AgentResult {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName 不能为空");
        }
        agentName = agentName.strip();
        status = status == null ? Status.FAILED : status;
        summary = summary == null ? "" : summary.strip();
        facts = facts == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    public static AgentResult completed(
            String agentName,
            String summary,
            Map<String, String> facts) {
        return new AgentResult(agentName, Status.COMPLETED, summary, facts);
    }

    public static AgentResult failed(String agentName, String reason) {
        return new AgentResult(agentName, Status.FAILED, reason, Map.of());
    }

    public boolean completed() {
        return status == Status.COMPLETED;
    }
}
