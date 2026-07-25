package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.CompareProductsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryLogisticsTool;
import com.nageoffer.ai.tinyagent.react.tools.QueryOrderTool;
import com.nageoffer.ai.tinyagent.react.tools.RecommendBundleTool;
import com.nageoffer.ai.tinyagent.react.tools.SearchKnowledgeTool;

import java.util.List;

/**
 * 比特严选专家团队工厂。按第 21 篇定的三个专家，把四要素按领域切开：
 * 每个专家一份独立人设、一套最小充分的工具子集、各自干净的上下文
 * <p>
 * 划分讲究两点：一是按人设差异切（售后的严谨和导购的主见塞一份 Prompt 会打架），
 * 二是工具子集最小充分（商品专家不给 applyRefund，候选越少选对概率越高）
 * <p>
 * 知识检索这里用 Mock 的 {@link SearchKnowledgeTool}，好让 Demo 只靠一个 API Key 就能跑；
 * 生产环境应把这个槽位换成第 20 篇的向量检索 {@code RagSearchTool}（需要 pgvector + Embedding）
 * 每个专家各 new 一个自己的 SearchKnowledgeTool 实例，工具子集之间互不共享
 */
public final class BitMallSpecialists {

    private BitMallSpecialists() {
    }

    /**
     * 商品咨询专家：专业、会对比、有主见的选购顾问
     */
    public static SpecialistAgent product(LlmClient llmClient) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new CompareProductsTool());
        tools.register(new SearchKnowledgeTool());

        String persona = """
                你是比特严选的选购顾问，专业、懂产品、有主见。
                - 用户要对比商品时，用 compareProducts 拿到两款的结构化规格，再逐项对比，不要凭印象下结论。
                - 需要选购建议、功能介绍、适用人群这类知识性信息时，用 searchKnowledge 检索。
                - 结合用户说的使用场景（通勤、运动、老人用等）给出明确的推荐，别把选择题原样甩回给用户。
                - 回复面向用户，简洁友好，不要暴露工具名、JSON 等内部细节。
                """;

        return new SpecialistAgent(
                "productSpecialist",
                "商品咨询专家：负责商品规格对比、选购建议、功能咨询。用户问某款产品好不好、两款怎么选、"
                        + "参数差异、适合什么人用时找他。",
                persona, llmClient, tools, 8, 6000);
    }

    /**
     * 售后服务专家：严谨、守政策、按流程办事
     */
    public static SpecialistAgent afterSales(LlmClient llmClient) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new QueryOrderTool());
        tools.register(new QueryLogisticsTool());
        tools.register(new ApplyRefundTool());

        String persona = """
                你是比特严选的售后专员，严谨、守政策、按流程办事。
                - 查订单状态用 queryOrder；查物流轨迹用 queryLogistics（需要运单号，运单号通常来自订单详情）。
                - 退款用 applyRefund，但必须先用 queryOrder 确认订单状态：已签收才可申请退款；
                  未签收或订单不存在，如实告知用户，不要硬退。
                - reason 依据用户描述如实填写。
                - 一步步来，每次调用工具后看结果再决定下一步。回复面向用户，不要暴露工具名、JSON。
                """;

        return new SpecialistAgent(
                "afterSalesSpecialist",
                "售后服务专家：负责查订单、查物流、退款换货。用户问订单到了没、物流到哪了、要退货退款、"
                        + "售后进度时找他。",
                persona, llmClient, tools, 8, 6000);
    }

    /**
     * IoT 搭配专家：懂生态、会跨品类组合、算得清组合价
     */
    public static SpecialistAgent iot(LlmClient llmClient) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new RecommendBundleTool());
        tools.register(new SearchKnowledgeTool());

        String persona = """
                你是比特严选的 IoT 搭配顾问，懂生态、会跨品类组合、算得清组合价。
                - 用户想给某个设备配套时，用 recommendBundle（baseProduct 传用户已有或感兴趣的商品），
                  拿到搭配方案和组合价。
                - 需要生态玩法、设备互联能力这类背景知识时，用 searchKnowledge。
                - 结合用户偏好（运动健康、全屋智能等）从方案里挑最合适的推荐，说清能省多少、为什么这么搭。
                - 回复面向用户，简洁友好，不要暴露工具名、JSON 等内部细节。
                """;

        return new SpecialistAgent(
                "iotSpecialist",
                "IoT 搭配专家：负责智能硬件的跨品类生态搭配、套装组合推荐与组合价测算。用户想给已有设备配一套、"
                        + "问怎么搭配、要套装方案时找他。",
                persona, llmClient, tools, 8, 6000);
    }

    /**
     * 一次性拿到整支专家团队，Demo 里的两种路由都复用它
     */
    public static List<Agent> team(LlmClient llmClient) {
        return List.of(product(llmClient), afterSales(llmClient), iot(llmClient));
    }
}
