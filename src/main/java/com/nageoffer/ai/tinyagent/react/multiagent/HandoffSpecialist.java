package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.ReActAgent;

import java.util.List;
import java.util.Map;

/**
 * 把一位领域专家接进结构化交接链路
 * <p>
 * 它做两件 {@link HandoffAgent#from(Agent)} 做不到的事：
 * <ul>
 *     <li>用 {@link ReActAgent.TerminationStatus} 如实决定 status，不再把跑满步数、原地打转
 *         这类未收敛的情况一律标成 COMPLETED；</li>
 *     <li>用 {@link ToolFactExtractor} 从本轮工具返回里确定性抽出 facts，
 *         下游拿到的是工具字段，不是模型复述。</li>
 * </ul>
 */
public class HandoffSpecialist implements HandoffAgent {

    private final SpecialistAgent specialist;
    private final ToolFactExtractor factExtractor;

    public HandoffSpecialist(SpecialistAgent specialist, ToolFactExtractor factExtractor) {
        if (specialist == null) {
            throw new IllegalArgumentException("specialist 不能为空");
        }
        this.specialist = specialist;
        this.factExtractor = factExtractor == null
                ? new ToolFactExtractor(List.of())
                : factExtractor;
    }

    @Override
    public String name() {
        return specialist.name();
    }

    @Override
    public String description() {
        return specialist.description();
    }

    @Override
    public String run(String task) {
        return execute(task).summary();
    }

    @Override
    public AgentResult execute(String task) {
        ReActAgent.RunResult run = specialist.runDetailed(task);

        // ReAct 没有正常收敛就是失败，不能让下游把一句道歉当成结论继续用
        if (!run.completed()) {
            return AgentResult.failed(
                    name(),
                    "ReAct 循环未正常收敛，终止原因 " + run.status());
        }

        Map<String, String> facts = factExtractor.extract(run.observations());
        System.out.println("--- [" + name() + "] 抽取到 " + facts.size()
                + " 条工具事实：" + facts.keySet());
        return AgentResult.completed(name(), run.answer(), facts);
    }
}
