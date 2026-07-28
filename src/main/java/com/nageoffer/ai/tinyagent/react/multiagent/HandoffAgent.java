package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.Map;

/**
 * 能参与结构化 Handoff 的 Agent
 */
public interface HandoffAgent extends Agent {

    AgentResult execute(String task);

    /**
     * 把第 22 篇那种只有 {@code run(String)} 的普通 Agent 适配进来
     * <p>
     * 这个适配是有损的，用之前先看清楚损在哪：普通 Agent 只交出一段文本，
     * 所以 facts 只能是空的，status 也只能一律填 COMPLETED——哪怕它内部其实跑满了步数
     * 只返回了一句道歉。下游因此既拿不到工具字段，也分不出这次到底成没成
     * <p>
     * 真要参与依赖交接，用 {@link HandoffSpecialist}：它从 ReAct 的终止状态判定 status，
     * 从工具返回里抽 facts。{@code from} 只适合那些不被任何人依赖的收尾型 Agent
     */
    static HandoffAgent from(Agent agent) {
        return new HandoffAgent() {
            @Override
            public String name() {
                return agent.name();
            }

            @Override
            public String description() {
                return agent.description();
            }

            @Override
            public String run(String task) {
                return agent.run(task);
            }

            @Override
            public AgentResult execute(String task) {
                return AgentResult.completed(
                        agent.name(),
                        agent.run(task),
                        Map.of());
            }
        };
    }
}
