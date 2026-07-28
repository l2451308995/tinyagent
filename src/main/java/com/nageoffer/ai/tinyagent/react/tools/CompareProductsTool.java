package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class CompareProductsTool implements Tool {

    private static final Map<String, String> PRODUCTS = new LinkedHashMap<>();

    static {
        PRODUCTS.put("比特 S10 Lite 扫地机",
                "{\"name\":\"比特 S10 Lite 扫地机\",\"category\":\"智能家居\","
                // Lite 这条刻意不给 control：Pro 的 control 是一个完整答案，Lite 的不是，
                // 同名字段并排放进对比表就等于宣称两者可比，模型会把“机身按键”读成
                // “没有 App”。能声明的只有机身操作方式，App 能力交给 appControlStatus
                // 机身语音播报同理，只有 Lite 这条有，模型就会给 Pro 的那一列补一句
                // “资料未说明”，紧挨着 IoT 那节“Pro 支持语音控制”一起读非常别扭；
                // 而且这个维度归 IoT 判断，本来就不该出现在商品规格里
                + "\"price\":1599,\"navigation\":\"基础导航\","
                + "\"onDeviceOperation\":\"机身按键\","
                + "\"appControlStatus\":\"UNKNOWN\","
                + "\"dustCollection\":\"手动清理尘盒\",\"charging\":\"自动回充\","
                + "\"features\":\"一键启停，适合操作从简的家庭\"}");
        // 两条规格原来都带 compatibilityDataIncluded:false，本意是提醒模型别谈兼容，
        // 实际效果相反：字段一出现，模型就要对它表态，写出“两款都没有兼容性数据”，
        // 和真正查了兼容知识库的 IoT 那一节直接打架。不归你管的维度，字段里就不该有
        PRODUCTS.put("比特 S10 Pro 扫地机",
                "{\"name\":\"比特 S10 Pro 扫地机\",\"category\":\"智能家居\","
                + "\"price\":1999,\"navigation\":\"激光导航\",\"control\":\"比特智能 App 远程控制\","
                + "\"appControlStatus\":\"SUPPORTED\","
                + "\"dustCollection\":\"自动集尘\",\"charging\":\"自动回充\","
                + "\"features\":\"地图规划，自动集尘\"}");
        // S10 Pro 的升级款，换新候选里顶替 S10 Pro 的位置
        PRODUCTS.put("比特 S11 Pro 扫地机",
                "{\"name\":\"比特 S11 Pro 扫地机\",\"category\":\"智能家居\","
                + "\"price\":1899,\"navigation\":\"激光导航\",\"control\":\"比特智能 App 远程控制\","
                + "\"appControlStatus\":\"SUPPORTED\","
                + "\"dustCollection\":\"自动集尘\",\"charging\":\"自动回充\","
                + "\"features\":\"地图规划，自动集尘，断连自动重连\"}");
        PRODUCTS.put("比特 AirX 真无线耳机",
                "{\"name\":\"比特 AirX 真无线耳机\",\"category\":\"智能穿戴\","
                + "\"price\":399,\"type\":\"真无线入耳式\","
                + "\"noiseCancelling\":\"主动降噪 ANC\",\"battery\":\"单次 8 小时，总续航 30 小时\","
                + "\"weight\":\"5.2g（单耳）\",\"waterproof\":\"IPX4\","
                + "\"features\":\"蓝牙 5.3，通话降噪，触控操作，支持快充\"}");
        PRODUCTS.put("比特 BandPro 头戴式耳机",
                "{\"name\":\"比特 BandPro 头戴式耳机\",\"category\":\"智能穿戴\","
                + "\"price\":699,\"type\":\"头戴式包耳\","
                + "\"noiseCancelling\":\"混合主动降噪 + 通透模式\",\"battery\":\"40 小时\","
                + "\"weight\":\"258g\",\"waterproof\":\"无\","
                + "\"features\":\"Hi-Res 认证，可折叠，3.5mm 有线模式，多设备连接\"}");
        PRODUCTS.put("比特 Pad Air 平板",
                "{\"name\":\"比特 Pad Air 平板\",\"category\":\"平板电脑\","
                + "\"price\":2499,\"screenSize\":\"11 英寸 IPS\","
                + "\"processor\":\"天玑 8300\",\"ram\":\"8GB\",\"storage\":\"128GB\","
                + "\"battery\":\"8000mAh\",\"features\":\"支持手写笔，分屏多任务，护眼模式\"}");
        PRODUCTS.put("比特 Phone S1 手机",
                "{\"name\":\"比特 Phone S1 手机\",\"category\":\"手机\","
                + "\"price\":1999,\"screenSize\":\"6.7 英寸 AMOLED\","
                + "\"processor\":\"骁龙 7 Gen3\",\"ram\":\"12GB\",\"storage\":\"256GB\","
                + "\"battery\":\"5000mAh\",\"camera\":\"5000 万像素主摄 + 800 万超广角\","
                + "\"features\":\"120Hz 刷新率，NFC，红外遥控\"}");
        PRODUCTS.put("比特 WatchFit 智能手表",
                "{\"name\":\"比特 WatchFit 智能手表\",\"category\":\"智能穿戴\","
                + "\"price\":599,\"screenSize\":\"1.82 英寸 AMOLED\","
                + "\"battery\":\"典型使用 14 天\",\"waterproof\":\"5ATM\","
                + "\"features\":\"心率血氧监测，100+ 运动模式，NFC 公交支付，消息通知\"}");
        PRODUCTS.put("比特 SoundBox Mini 智能音箱",
                "{\"name\":\"比特 SoundBox Mini 智能音箱\",\"category\":\"智能家居\","
                + "\"price\":299,\"features\":\"语音助手，全屋智能控制中枢，蓝牙 + WiFi，闹钟定时\"}");
    }

    @Override
    public String name() {
        return "compareProducts";
    }

    @Override
    public String description() {
        return "对比两个比特严选商品的规格参数，返回两者的详细规格信息供对比分析。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "productA": {
                      "type": "string",
                      "description": "第一个商品名称，如 比特 AirX 真无线耳机"
                    },
                    "productB": {
                      "type": "string",
                      "description": "第二个商品名称，如 比特 BandPro 头戴式耳机"
                    }
                  },
                  "required": ["productA", "productB"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String nameA = ToolUtils.extractRequiredField(input, "productA");
        if (nameA.isBlank()) {
            return ToolUtils.missingRequiredField("productA");
        }
        String nameB = ToolUtils.extractRequiredField(input, "productB");
        if (nameB.isBlank()) {
            return ToolUtils.missingRequiredField("productB");
        }

        String specA = fuzzyMatch(nameA);
        String specB = fuzzyMatch(nameB);

        if (specA == null && specB == null) {
            return "{\"error\":" + ToolUtils.toJsonString("未找到商品：" + nameA + " 和 " + nameB) + "}";
        }
        if (specA == null) {
            return "{\"error\":" + ToolUtils.toJsonString("未找到商品：" + nameA) + ",\"productB\":" + specB + "}";
        }
        if (specB == null) {
            return "{\"error\":" + ToolUtils.toJsonString("未找到商品：" + nameB) + ",\"productA\":" + specA + "}";
        }

        return "{\"productA\":" + specA + ",\"productB\":" + specB + "}";
    }

    private String fuzzyMatch(String keyword) {
        String normalized = keyword == null ? "" : keyword.strip();
        String exact = PRODUCTS.get(normalized);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, String> entry : PRODUCTS.entrySet()) {
            if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        Map.Entry<String, String> bestMatch = null;
        int bestScore = 1;
        for (Map.Entry<String, String> entry : PRODUCTS.entrySet()) {
            int score = matchedKeyParts(entry.getKey(), normalized);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry;
            }
        }
        return bestMatch == null ? null : bestMatch.getValue();
    }

    private int matchedKeyParts(String productName, String keyword) {
        String lower = keyword.toLowerCase();
        String pLower = productName.toLowerCase();
        String[] parts = pLower.split("\\s+");
        int matched = 0;
        for (String part : parts) {
            if (lower.contains(part)) {
                matched++;
            }
        }
        return matched;
    }
}
