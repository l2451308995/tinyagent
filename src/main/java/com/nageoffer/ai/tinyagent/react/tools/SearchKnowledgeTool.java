package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class SearchKnowledgeTool implements Tool {

    public enum KnowledgeDomain {
        ALL,
        PRODUCT,
        AFTER_SALES,
        IOT
    }

    private final KnowledgeDomain domain;

    public SearchKnowledgeTool() {
        this(KnowledgeDomain.ALL);
    }

    public SearchKnowledgeTool(KnowledgeDomain domain) {
        this.domain = domain == null ? KnowledgeDomain.ALL : domain;
    }

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return switch (domain) {
            case PRODUCT -> "检索商品选购知识，只返回产品信息、价格和适用场景。";
            case AFTER_SALES -> "检索售后知识，只返回保修、退货、维修和故障排查政策。";
            case IOT -> "检索 IoT 兼容知识，只返回设备联动能力、指令和配置边界。";
            case ALL -> "检索比特严选知识库，返回匹配的售后政策、常见问题或产品信息。";
        };
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "搜索关键词或问题，如 扫地机 退货政策"
                    }
                  },
                  "required": ["query"]
                }""";
    }

    // 当前系列以 Agent 流程为主，RAG 检索仅提供 Mock 数据，按关键词返回预设结果
    @Override
    public String invoke(String input) {
        String query = ToolUtils.extractField(input, "query");
        if (query.isBlank()) {
            return ToolUtils.missingRequiredField("query");
        }
        String lowerQuery = query.toLowerCase();

        if (allows(KnowledgeDomain.AFTER_SALES)
                && lowerQuery.contains("扫地机") && (lowerQuery.contains("保修")
                || lowerQuery.contains("售后") || lowerQuery.contains("掉线")
                || lowerQuery.contains("质量") || lowerQuery.contains("维修")
                || lowerQuery.contains("换新") || lowerQuery.contains("退款")
                || lowerQuery.contains("退货"))) {
            return "{\"matched\":\"S10 系列售后与掉线处理规范\","
                    + "\"wifiTroubleshootingInstruction\":\"排查时确认路由器已开启 2.4GHz Wi-Fi\","
                    + "\"wifi5GHzSupportStatus\":\"UNKNOWN\","
                    + "\"selfCheckSuccessRate\":null,"
                    + "\"content\":\"比特 S10 系列整机保修 1 年，自签收日起计算。"
                    + "频繁掉线时，先确认路由器已开启 2.4GHz Wi-Fi，并在比特智能 App 中检查固件、"
                    + "按 App 指引重置网络后重新配网；仍然掉线则申请售后检测。"
                    + "当前资料没有说明是否支持 5GHz，也没有提供上述排查步骤的成功率，不能据此扩写。"
                    + "保修期内经检测确认为非人为硬件故障，可免费维修；是否换新或退款以检测结果和售后审核为准，"
                    + "不能在检测前承诺。七天无理由退货仅适用于签收次日起 7 个自然日内且商品符合完好条件的情形。\"}";
        }

        if (allows(KnowledgeDomain.IOT)
                && lowerQuery.contains("扫地机")
                && (lowerQuery.contains("soundbox") || lowerQuery.contains("音箱"))
                && (lowerQuery.contains("联动") || lowerQuery.contains("语音")
                || lowerQuery.contains("兼容"))) {
            return "{\"matched\":\"SoundBox Mini 与扫地机兼容说明\","
                    + "\"s11ProSoundBoxControlStatus\":\"SUPPORTED\","
                    + "\"s10LiteSoundBoxControlStatus\":\"UNKNOWN\","
                    + "\"roomLevelVoiceCleaningStatus\":\"UNKNOWN\","
                    + "\"gatewayRequirementStatus\":\"UNKNOWN\","
                    + "\"content\":\"比特 S11 Pro 可在比特智能 App 中绑定，并与同一账号下的 "
                    + "SoundBox Mini 联动，支持开始清扫、暂停清扫和返回充电三类语音指令。"
                    + "首次使用需要完成扫地机配网、账号绑定和设备授权，联动时两台设备都需要在线。"
                    + "S10 Lite 的语音播报是机器自身的状态提示，不等于接受智能音箱控制；"
                    + "当前资料未声明它支持 SoundBox Mini 联动。当前资料也未声明房间级语音清扫和网关要求，"
                    + "这些能力只能回答资料未说明，不能改写成不支持或不需要。\"}";
        }

        if (allows(KnowledgeDomain.PRODUCT)
                && lowerQuery.contains("扫地机") && (lowerQuery.contains("推荐")
                || lowerQuery.contains("老人") || lowerQuery.contains("操作")
                || lowerQuery.contains("2000") || lowerQuery.contains("产品")
                || lowerQuery.contains("候选") || lowerQuery.contains("选购")
                || lowerQuery.contains("型号") || lowerQuery.contains("替代")
                || lowerQuery.contains("预算"))) {
            return "{\"matched\":\"扫地机选购指南\","
                    + "\"catalogScope\":\"比特商城在售扫地机\","
                    + "\"maxPrice\":2000,"
                    + "\"candidateSetStatus\":\"COMPLETE\","
                    + "\"candidateCount\":2,"
                    // 候选集的权威来源就是这张目录，所以它必须自己给出结构化的候选列表
                    // 早先是从 compareProducts 的 productA/productB 抽候选，那是错的：
                    // 那两个字段的语义是“这次比了谁”，模型多比一次，候选事实就被换掉
                    + "\"candidates\":["
                    + "{\"name\":\"比特 S10 Lite 扫地机\",\"price\":1599,"
                    + "\"appControlStatus\":\"UNKNOWN\"},"
                    + "{\"name\":\"比特 S11 Pro 扫地机\",\"price\":1899,"
                    + "\"appControlStatus\":\"SUPPORTED\"}],"
                    // 目录里刻意不含 S10 Pro：它是用户正在用、正在掉线的那台，
                    // 把在用同款列进换新候选，模型就会一本正经地推荐“换一台一模一样的”
                    + "\"content\":\"比特 S10 Lite 扫地机（¥1599）：一键启停，语音播报，"
                    + "自动回充，适合老年人使用；比特 S11 Pro 扫地机（¥1899）：激光导航，"
                    + "App 远程控制，自动集尘，S10 Pro 的升级款，适合年轻家庭。\"}";
        }

        if (allows(KnowledgeDomain.PRODUCT) && lowerQuery.contains("耳机")) {
            return "{\"matched\":\"耳机产品列表\","
                    + "\"content\":\"比特 AirX 真无线耳机（¥399）：主动降噪，30 小时续航，"
                    + "蓝牙 5.3，IPX4 防水，适合运动和通勤；"
                    + "比特 BandPro 头戴式耳机（¥699）：Hi-Res 认证，可折叠设计，"
                    + "混合主动降噪 + 通透模式，40 小时续航，适合长时间音乐欣赏和办公。\"}";
        }

        if (allows(KnowledgeDomain.PRODUCT)
                && (lowerQuery.contains("手表") || lowerQuery.contains("穿戴"))) {
            return "{\"matched\":\"智能穿戴产品列表\","
                    + "\"content\":\"比特 WatchFit 智能手表（¥599）：1.82 英寸 AMOLED，"
                    + "续航 14 天，5ATM 防水，心率血氧监测，100+ 运动模式，NFC 公交支付。\"}";
        }

        if (allows(KnowledgeDomain.PRODUCT) && lowerQuery.contains("手机")) {
            return "{\"matched\":\"手机产品列表\","
                    + "\"content\":\"比特 Phone S1 手机（¥1999）：6.7 英寸 AMOLED，"
                    + "骁龙 7 Gen3，12GB+256GB，5000mAh，120Hz 刷新率，NFC，红外遥控。\"}";
        }

        if (allows(KnowledgeDomain.IOT)
                && (lowerQuery.contains("搭配") || lowerQuery.contains("套装")
                || lowerQuery.contains("生态") || lowerQuery.contains("组合"))) {
            return "{\"matched\":\"IoT 生态搭配指南\","
                    + "\"content\":\"比特严选 IoT 生态支持手机、手表、耳机、音箱四大品类互联。"
                    + "手机作为控制中枢，手表同步健康数据，耳机无缝切换连接，音箱做语音控制入口。"
                    + "推荐搭配方案请使用 recommendBundle 工具获取详细组合和优惠价。\"}";
        }

        return "{\"matched\":null,"
                + "\"content\":\"未找到与该问题直接相关的知识。请缩小查询范围，"
                + "或明确告知用户当前资料不足，不能补全未查询到的事实。\"}";
    }

    private boolean allows(KnowledgeDomain required) {
        return domain == KnowledgeDomain.ALL || domain == required;
    }
}
