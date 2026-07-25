package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;

import java.util.List;

/**
 * 路由策略二：LLM Supervisor（中央主管，agent card 循环选择直到 done）
 * <p>
 * 这里把「子 Agent 即工具」的同构用到极致：把每个专家用 {@link AgentAsTool} 包成工具，
 * 塞进一个 {@link ToolRegistry}，再用一份主管人设构造一个普通的 {@link ReActAgent}
 * 于是「读 agent card → 选一个专家 → 观察它的结果 → 再选下一个 → 收齐后综合输出」
 * 恰好就是我们从第 5 篇就写好的那套 ReAct 循环，一行新循环都不用写
 * <p>
 * 主管不自己查数据、不自己回答专业问题，它是纯粹的调度 + 综合节点——这一步综合天然是个
 * 校验瓶颈，专家的输出要经它核对、去重、消解冲突才给到用户，正是主从式错误可控的来源
 * <p>
 * 它自己也实现 {@link Agent}，所以一个主管可以作为另一个更高层主管的下属，架构能向上嵌套
 */
public class SupervisorAgent implements Agent {

    private static final String SUPERVISOR_PERSONA = """
            你是比特严选的客服主管（Orchestrator）。你自己不查订单、不比价、不回答专业问题，
            你的职责是调度团队里的领域专家，并把他们的结论综合成一段面向用户的完整回复。
            你的每一个工具都是一位领域专家，工具描述说明了他擅长什么。
            
            工作方式：
            - 先分析用户请求涉及哪些领域。一个请求可能横跨多个领域（比如既要对比商品、又要搭配推荐）。
            - 把每个子任务交给对口的专家：调用对应的工具，task 参数里写清这个子任务，
              并带上所有必要上下文（用户原话、涉及的商品名、订单号、预算等）。专家看不到你这边的对话，
              上下文没给全他就办不了。
            - 无依赖的子任务可以分别派给不同专家；需要前一步结果的子任务，等结果回来再派下一个。
            - 收齐所有专家的结果后，你负责核对、去重、消解可能的冲突，综合成一段自然、连贯的回复。
            - 不要把专家返回的原始 JSON、工具名、内部处理过程暴露给用户。
            
            重要：当所有子任务都已由专家完成、你已经能给出完整答复时，直接输出面向用户的最终回复，
            不要再调用任何专家。不要为了同一个子任务重复调用同一个专家。
            """;

    private static final String DEFAULT_NAME = "supervisor";
    private static final String DEFAULT_DESCRIPTION =
            "比特严选客服主管：把用户请求分诊给对口专家，收齐结果后综合成完整回复。";

    private final String name;
    private final String description;
    private final ReActAgent delegate;

    public SupervisorAgent(String name, String description,
                           LlmClient llmClient, List<Agent> specialists,
                           int maxSteps, int maxTokens) {
        this.name = name;
        this.description = description;
        ToolRegistry team = new ToolRegistry();
        for (Agent specialist : specialists) {
            team.register(new AgentAsTool(specialist));
        }
        this.delegate = new ReActAgent(llmClient, team,
                SUPERVISOR_PERSONA, maxSteps, maxTokens);
    }

    public SupervisorAgent(LlmClient llmClient, List<Agent> specialists,
                           int maxSteps, int maxTokens) {
        this(DEFAULT_NAME, DEFAULT_DESCRIPTION, llmClient, specialists, maxSteps, maxTokens);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String run(String userMessage) {
        System.out.println("\n########## 主管接手请求：" + userMessage);
        return delegate.run(userMessage);
    }
}
