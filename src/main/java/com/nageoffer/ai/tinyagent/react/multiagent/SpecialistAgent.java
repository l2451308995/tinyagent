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
        System.out.println("\n>>> [" + name + "] 接手子任务：" + userMessage);
        String result = delegate.run(userMessage);
        System.out.println("<<< [" + name + "] 交回结果");
        return result;
    }
}
