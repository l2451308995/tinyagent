package com.nageoffer.ai.tinyagent.react.multiagent;

import com.nageoffer.ai.tinyagent.react.LlmClient;

import java.util.List;

/**
 * 给第 21、22 篇那三位专家各配一张事实抽取规则表，让他们能参与结构化交接
 * <p>
 * 人设和工具子集完全复用 {@link BitMallSpecialists}，这里只补通信层需要的那一半：
 * 哪些工具字段可以作为已确认事实交给下游。规则表刻意写得很窄——
 * 只收工具能证明的字段，把“看起来也挺有用”的自然语言留在 summary 里
 */
public final class BitMallHandoffTeam {

    private BitMallHandoffTeam() {
    }

    /**
     * 售后专家：订单事实来自 queryOrder，保修结论来自 checkWarranty 的确定性日期计算
     * 保修状态坚决不从 searchKnowledge 的政策原文里抠，那是政策不是本单结论
     */
    public static HandoffSpecialist afterSales(LlmClient llmClient) {
        return new HandoffSpecialist(
                BitMallSpecialists.afterSales(llmClient),
                ToolFactExtractor.of(
                        "queryOrder", "orderId", "orderId",
                        "queryOrder", "product", "productName",
                        "queryOrder", "price", "originalPrice",
                        "queryOrder", "signTime", "signDate",
                        "queryOrder", "status", "orderStatus",
                        "checkWarranty", "warrantyStatus", "warrantyStatus",
                        "checkWarranty", "warrantyEndDate", "warrantyEndDate",
                        "checkWarranty", "noReasonReturnStatus", "noReasonReturnStatus"));
    }

    /**
     * 商品专家：候选事实全部来自 searchKnowledge 的在售目录
     * compareProducts 一条规则都不配，它只负责给用户看的规格对比
     * 因为它的 productA/productB 表示的是“这次比了谁”，模型为了说清升级关系
     * 会拿在用型号再比一轮，位置型字段一被覆盖，候选就从在售款漂成在用款
     */
    public static HandoffSpecialist product(LlmClient llmClient) {
        return new HandoffSpecialist(
                BitMallSpecialists.product(llmClient),
                ToolFactExtractor.of(
                        "searchKnowledge", "candidates.0.name", "candidateA",
                        "searchKnowledge", "candidates.0.price", "candidateAPrice",
                        "searchKnowledge", "candidates.0.appControlStatus", "candidateAAppControlStatus",
                        "searchKnowledge", "candidates.1.name", "candidateB",
                        "searchKnowledge", "candidates.1.price", "candidateBPrice",
                        "searchKnowledge", "candidates.1.appControlStatus", "candidateBAppControlStatus",
                        "searchKnowledge", "catalogScope", "catalogScope",
                        "searchKnowledge", "candidateSetStatus", "candidateSetStatus",
                        "searchKnowledge", "candidateCount", "candidateCount"));
    }

    /**
     * IoT 专家：兼容结论只认工具返回的那几个显式状态位
     * UNKNOWN 也是事实，必须原样交出去，否则下游会把“资料没说”默认成“不支持”
     */
    public static HandoffSpecialist iot(LlmClient llmClient) {
        return new HandoffSpecialist(
                BitMallSpecialists.iot(llmClient),
                ToolFactExtractor.of(
                        "searchKnowledge", "s11ProSoundBoxControlStatus", "s11ProSoundBoxControlStatus",
                        "searchKnowledge", "s10LiteSoundBoxControlStatus", "s10LiteSoundBoxControlStatus",
                        "searchKnowledge", "roomLevelVoiceCleaningStatus", "roomLevelVoiceCleaningStatus",
                        "searchKnowledge", "gatewayRequirementStatus", "gatewayRequirementStatus",
                        "recommendBundle", "status", "bundleStatus",
                        "recommendBundle", "bundles.0.soundBoxControlStatus", "bundleSoundBoxControlStatus",
                        "recommendBundle", "bundles.0.additionalPurchasePrice", "bundleAdditionalPrice"));
    }

    public static List<HandoffAgent> team(LlmClient llmClient) {
        return List.of(afterSales(llmClient), product(llmClient), iot(llmClient));
    }
}
