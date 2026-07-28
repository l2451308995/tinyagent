package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ReActAgent;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;

/**
 * 领域专家：一个绑定了人设和工具子集的 {@link ReActAgent}
 * 它不是新写一套循环，而是复用第 5 篇就有的 ReAct 循环，只是换了人设、换了工具箱
 * 这正是多智能体第一性原理的落地——把单体那份揉在一起的四要素，按领域切成干净的一套
 */
public class SpecialistAgent implements Agent {

    private final String name;
    private final String description;
    private final ReActAgent delegate;

    public SpecialistAgent(String name, String description, ReActAgent delegate) {
        this.name = name;
        this.description = description;
        this.delegate = delegate;
    }

    /**
     * 便利构造器：给定人设和工具子集，内部建一个带人设的 ReActAgent
     * 专家默认不挂会话记忆——单轮内的请求-响应，隔离上下文正是我们要的
     */
    public SpecialistAgent(String name, String description, String persona,
                           LlmClient llmClient, ToolRegistry tools,
                           int maxSteps, int maxTokens) {
        this(name, description,
                new ReActAgent(llmClient, tools, persona, maxSteps, maxTokens));
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
        return runDetailed(userMessage).answer();
    }

    /**
     * 除了最终答复，把本轮真正调用过的工具证据一并交出来
     * 第 22 篇的主管只需要 {@link #run(String)} 的那段文本；第 23 篇的结构化交接要靠这里的
     * observations 抽 facts，否则下游只能拿到模型整理过的自然语言
     */
    public ReActAgent.RunResult runDetailed(String userMessage) {
        System.out.println("\n>>> [" + name + "] 接手子任务：" + userMessage);
        ReActAgent.RunResult result = delegate.runDetailed(userMessage);
        System.out.println("<<< [" + name + "] 交回结果，收敛状态 " + result.status()
                + "，工具证据 " + result.observations().size() + " 条");
        return result;
    }
}
