package com.nageoffer.ai.tinyagent.react.multiagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定依赖 Handoff：编排者一次性派发全部子任务，每个 Agent 从自己的邮箱取任务，
 * 执行前只能读到显式声明过的前序结果
 * <p>
 * 这个类演示通信语义，不替代第 22 篇 Supervisor 的自主路由
 */
public final class HandoffRunner {

    public static final String ORCHESTRATOR = "orchestrator";

    /**
     * @param projectUpstreamSummary 是否把上游的自然语言结论一起注入给这一步
     *                               默认关，原因见 {@link AgenticScope#briefingFor(String, Set, boolean)}
     */
    public record Step(
            String agentName,
            String task,
            Set<String> dependencies,
            boolean projectUpstreamSummary) {

        public Step(String agentName, String task) {
            this(agentName, task, Set.of(), false);
        }

        public Step(String agentName, String task, Set<String> dependencies) {
            this(agentName, task, dependencies, false);
        }

        public Step {
            if (agentName == null || agentName.isBlank()) {
                throw new IllegalArgumentException("agentName 不能为空");
            }
            agentName = agentName.strip();
            task = task == null ? "" : task.strip();
            dependencies = dependencies == null
                    ? Set.of()
                    : Collections.unmodifiableSet(
                    new LinkedHashSet<>(dependencies));
        }
    }

    /**
     * @param executedAgents      真正执行过的 Agent，按执行顺序
     * @param skippedAgents       因为上游失败而没有执行的 Agent
     * @param undeliveredTasks    中断时还躺在各自邮箱里没被消费的 TASK 条数
     * @param resultNotifications 投给聚合器的 RESULT_READY 和 RESULT_FAILED
     */
    public record RunResult(
            AgenticScope scope,
            List<String> executedAgents,
            List<String> skippedAgents,
            int undeliveredTasks,
            List<AgentMessage> resultNotifications) {
    }

    private final Map<String, HandoffAgent> agents;

    public HandoffRunner(List<HandoffAgent> agents) {
        Map<String, HandoffAgent> indexed = new LinkedHashMap<>();
        for (HandoffAgent agent : agents) {
            HandoffAgent previous = indexed.put(agent.name(), agent);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Agent 名称重复：" + agent.name());
            }
        }
        this.agents = Map.copyOf(indexed);
    }

    public RunResult run(
            String userRequest,
            Map<String, String> constraints,
            List<Step> plan) {
        validatePlan(plan);

        AgenticScope scope = new AgenticScope(userRequest);
        scope.putConstraints(constraints);
        MessageHub hub = new MessageHub();

        // 计划一确定就把全部 TASK 派发出去，之后 Agent 各自从邮箱取
        // 发和收隔开，邮箱才是真的队列——中断时还剩几条没被消费也就有了确定答案
        for (Step step : plan) {
            hub.send(ORCHESTRATOR, step.agentName(),
                    AgentMessage.Type.TASK, step.task());
        }
        System.out.println("[编排] 已派发 " + plan.size() + " 条 TASK，等待各自消费");

        List<String> executedAgents = new ArrayList<>();
        List<String> skippedAgents = new ArrayList<>();
        boolean aborted = false;

        for (Step step : plan) {
            if (aborted) {
                skippedAgents.add(step.agentName());
                continue;
            }

            List<AgentMessage> inbox = hub.drain(step.agentName());
            if (inbox.size() != 1) {
                throw new IllegalStateException(
                        step.agentName() + " 应收到且只收到一条任务消息，实际 " + inbox.size());
            }
            AgentMessage task = inbox.getFirst();
            if (task.type() != AgentMessage.Type.TASK) {
                throw new IllegalStateException(
                        step.agentName() + " 邮箱里出现了非任务消息：" + task.type());
            }

            String briefing = scope.briefingFor(
                    task.content(), step.dependencies(), step.projectUpstreamSummary());
            System.out.println("\n========== 注入给 " + step.agentName()
                    + " 的上下文（依赖 " + step.dependencies() + "）==========");
            System.out.println(briefing);
            System.out.println("==========================================");

            AgentResult result = executeSafely(step, briefing);
            scope.publish(result);
            executedAgents.add(step.agentName());

            hub.send(
                    step.agentName(),
                    Aggregator.RECIPIENT,
                    result.completed()
                            ? AgentMessage.Type.RESULT_READY
                            : AgentMessage.Type.RESULT_FAILED,
                    step.agentName());

            if (!result.completed()) {
                System.out.println("[编排] " + step.agentName()
                        + " 未完成，停止后续步骤：" + result.summary());
                aborted = true;
            }
        }

        int undelivered = 0;
        for (String skipped : skippedAgents) {
            undelivered += hub.pending(skipped);
        }

        return new RunResult(
                scope,
                List.copyOf(executedAgents),
                List.copyOf(skippedAgents),
                undelivered,
                hub.drain(Aggregator.RECIPIENT));
    }

    /**
     * Agent 抛异常属于本次执行失败，不属于编排层协议出错，所以收敛成 FAILED 继续往下走流程
     */
    private AgentResult executeSafely(Step step, String briefing) {
        try {
            AgentResult result = agents.get(step.agentName()).execute(briefing);
            if (result == null) {
                return AgentResult.failed(step.agentName(), "Agent 返回了空结果");
            }
            if (!step.agentName().equals(result.agentName())) {
                return AgentResult.failed(
                        step.agentName(),
                        "结果署名与被调度的 Agent 不一致：" + result.agentName());
            }
            return result;
        } catch (RuntimeException e) {
            return AgentResult.failed(
                    step.agentName(),
                    "执行抛出异常：" + e.getMessage());
        }
    }

    private void validatePlan(List<Step> plan) {
        if (plan == null || plan.isEmpty()) {
            throw new IllegalArgumentException("执行计划不能为空");
        }

        Set<String> appeared = new LinkedHashSet<>();
        for (Step step : plan) {
            if (!agents.containsKey(step.agentName())) {
                throw new IllegalArgumentException(
                        "执行计划引用了不存在的 Agent：" + step.agentName());
            }
            if (!appeared.containsAll(step.dependencies())) {
                Set<String> missing = new LinkedHashSet<>(step.dependencies());
                missing.removeAll(appeared);
                throw new IllegalArgumentException(
                        step.agentName() + " 依赖尚未执行的 Agent：" + missing);
            }
            if (!appeared.add(step.agentName())) {
                throw new IllegalArgumentException(
                        "最小示例不允许重复调度同一 Agent：" + step.agentName());
            }
        }
    }
}
