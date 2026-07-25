package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

/**
 * 子 Agent 即工具：把一个 {@link Agent} 适配成一个 {@link Tool}
 * <p>
 * 这是让整套架构收敛的关键同构——对主管来说，一个专家和一个普通工具没有区别，
 * 都是「给一段输入、拿一段输出」。于是主管天然就是一个 ReActAgent，
 * 它的工具箱里装的不是 queryOrder、compareProducts，而是一个个专家
 * <p>
 * agent card 直接透传：{@link #name()} 用作 Function Calling 的函数名（小驼峰、无连字符，
 * 避开部分兼容网关对函数名字符集的处理差异），{@link #description()} 就是 LLM 判断
 * 该不该找这个专家的唯一依据
 */
public class AgentAsTool implements Tool {

    private final Agent agent;

    public AgentAsTool(Agent agent) {
        this.agent = agent;
    }

    @Override
    public String name() {
        return agent.name();
    }

    @Override
    public String description() {
        return agent.description();
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "task": {
                      "type": "string",
                      "description": "交给该专家处理的完整子任务描述，要自带所有必要上下文（用户原话、涉及的商品名、订单号等），因为专家看不到主管这边的对话历史"
                    }
                  },
                  "required": ["task"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String task = ToolUtils.extractRequiredField(input, "task");
        if (task.isBlank()) {
            return ToolUtils.missingRequiredField("task");
        }
        return agent.run(task);
    }
}
