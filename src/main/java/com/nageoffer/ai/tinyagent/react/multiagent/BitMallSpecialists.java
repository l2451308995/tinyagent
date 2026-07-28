package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.LlmClient;
import com.nageoffer.ai.tinyagent.react.ToolRegistry;
import com.nageoffer.ai.tinyagent.react.tools.ApplyRefundTool;
import com.nageoffer.ai.tinyagent.react.tools.CheckWarrantyTool;
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

    /**
     * 三个专家共用的一段 grounding 规则
     * 它约束的是「结论从哪来」，和领域无关，所以不该在每份人设里各写一遍：
     * 一是改一条要改三处，二是分开写就容易越写越长，最后变成对着某次错误输出打的补丁
     */
    private static final String GROUNDING_RULES = """
            - 结论只能来自本轮工具返回。工具报错或没有命中时，直接说明资料不足，不要用常识补全。
            - 工具用 SUPPORTED、UNSUPPORTED、UNKNOWN 三态描述一项能力。UNKNOWN 表示资料未声明，
              只能回答资料未说明、暂时无法确认，不能改写成不支持。
            - 只承诺当前工具箱能做到的事。工具箱里没有的操作（如代下单、代提交工单）不要主动提出代办。
            """;

    /**
     * ReAct 步进方式和面向用户的表达要求，同样三个专家一致
     */
    private static final String REPLY_STYLE = """
            - 一步步来，每次调用工具后看结果再决定下一步。回复面向用户，简洁友好，
              不要暴露工具名、JSON 等内部细节。
            """;

    private BitMallSpecialists() {
    }

    /**
     * 人设 = 领域角色和工具用法 + 通用 grounding + 通用表达要求
     * 这一层刻意不写「只交付什么、不要提什么」：那是某次请求的分工，属于编排层，
     * 写进人设就等于把一次任务的边界焊死在 Agent 身份上，换个请求会互相打架
     */
    private static String personaOf(String roleAndTools) {
        return roleAndTools + GROUNDING_RULES + REPLY_STYLE;
    }

    /**
     * 商品咨询专家：专业、会对比、有主见的选购顾问
     */
    public static SpecialistAgent product(LlmClient llmClient) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new CompareProductsTool());
        tools.register(new SearchKnowledgeTool(
                SearchKnowledgeTool.KnowledgeDomain.PRODUCT));

        String persona = personaOf("""
                你是比特严选的选购顾问，专业、懂产品、有主见。
                - 用户要对比商品时，用 compareProducts 拿到两款的结构化规格，再逐项对比，不要凭印象下结论。
                - 需要选购建议、功能介绍、适用人群这类知识性信息时，用 searchKnowledge 检索。
                - 结合用户说的使用场景（通勤、运动、老人用等）给出明确的推荐，别把选择题原样甩回给用户。
                - 说候选已经查全时，限定在工具返回的目录范围内表述，不要说成全站或全市场在售商品。
                """);

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
        tools.register(new SearchKnowledgeTool(
                SearchKnowledgeTool.KnowledgeDomain.AFTER_SALES));
        tools.register(new CheckWarrantyTool());
        tools.register(new ApplyRefundTool());

        String persona = personaOf("""
                你是比特严选的售后专员，严谨、守政策、按流程办事。
                - 查订单状态用 queryOrder；查物流轨迹用 queryLogistics（需要运单号，运单号通常来自订单详情）。
                - 保修期限、故障排查、维修、换新和退款条件必须用 searchKnowledge 查询，不能只看订单日期推断。
                - 判断是否在保、七天无理由是否到期，必须把订单签收日和政策期限交给 checkWarranty 计算，
                  不要依赖你记忆中的当前日期，也不要自己推算日期边界。
                - 只有用户明确要求现在提交退款，并且订单状态与政策都允许时，才能调用 applyRefund，
                  reason 依据用户描述如实填写。用户只是在咨询处理方式时不要擅自提交。
                - 排查步骤只是本次建议，不要写成产品的联网前提，也不要给出成功率之类的判断。
                """);

        return new SpecialistAgent(
                "afterSalesSpecialist",
                "售后服务专家：负责查订单、查物流、核验保修状态、故障排查、退款换货边界。"
                        + "用户问是否在保、故障怎么处理、订单物流或退款条件时找他。",
                persona, llmClient, tools, 8, 6000);
    }

    /**
     * IoT 搭配专家：懂生态、会跨品类组合、算得清组合价
     */
    public static SpecialistAgent iot(LlmClient llmClient) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new RecommendBundleTool());
        tools.register(new SearchKnowledgeTool(
                SearchKnowledgeTool.KnowledgeDomain.IOT));

        String persona = personaOf("""
                你是比特严选的 IoT 搭配顾问，懂生态、会跨品类组合、算得清组合价。
                - 用户想给某个设备配套时，用 recommendBundle（baseProduct 传用户已有或感兴趣的商品），
                  拿到搭配方案和组合价。
                - 需要生态玩法、设备互联能力这类背景知识时，用 searchKnowledge。
                - 结合用户偏好（运动健康、全屋智能等）从方案里挑最合适的推荐，说清能省多少、为什么这么搭。
                - 联动能力、可用指令、网关要求这类兼容结论必须由工具明确给出。支持 App、同一品牌、
                  有语音播报都不足以证明可以被智能音箱控制。
                - 用户把明确兼容当作硬约束、而证据又是 UNKNOWN 时，说清未确认前不建议购买，
                  不要替工具下判断。
                """);

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
