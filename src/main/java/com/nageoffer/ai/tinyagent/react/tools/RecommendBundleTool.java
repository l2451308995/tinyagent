package com.nageoffer.ai.tinyagent.react.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

public class RecommendBundleTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "recommendBundle";
    }

    @Override
    public String description() {
        return "根据用户已有或感兴趣的商品，推荐 IoT 生态搭配组合（手机 + 手表 + 音箱等跨品类组合），返回搭配方案和组合价。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "baseProduct": {
                      "type": "string",
                      "description": "用户已有或感兴趣的基础商品名称，如 比特 Phone S1 手机"
                    },
                    "budget": {
                      "type": "number",
                      "description": "搭配总预算（元），可选"
                    },
                    "preferences": {
                      "type": "string",
                      "description": "用户偏好描述，如 运动健康监测、全屋智能控制"
                    }
                  },
                  "required": ["baseProduct"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String baseProduct = ToolUtils.extractRequiredField(input, "baseProduct");
        if (baseProduct.isBlank()) {
            return ToolUtils.missingRequiredField("baseProduct");
        }
        Budget budget = parseBudget(input);
        if (!budget.valid()) {
            return "{\"error\":\"budget 必须是大于 0 的数字\"}";
        }

        String lower = baseProduct.toLowerCase();
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode bundles = result.putArray("bundles");

        if (lower.contains("phone") || lower.contains("手机")) {
            result.put("baseProduct", "比特 Phone S1 手机");
            if (budget.allows(2799)) {
                ObjectNode bundle = bundles.addObject();
                bundle.put("name", "运动健康套装");
                addItems(bundle, "比特 Phone S1 手机（¥1999）",
                        "比特 WatchFit 智能手表（¥599）",
                        "比特 AirX 真无线耳机（¥399）");
                bundle.put("totalPrice", 2997);
                bundle.put("bundlePrice", 2799);
                bundle.put("saving", 198);
                bundle.put("scenario", "手机接收手表的运动和健康数据，耳机连接手机听歌跑步");
            }
            if (budget.allows(2699)) {
                ObjectNode bundle = bundles.addObject();
                bundle.put("name", "全屋智能套装");
                addItems(bundle, "比特 Phone S1 手机（¥1999）",
                        "比特 SoundBox Mini 智能音箱（¥299）",
                        "比特 WatchFit 智能手表（¥599）");
                bundle.put("totalPrice", 2897);
                bundle.put("bundlePrice", 2699);
                bundle.put("saving", 198);
                bundle.put("scenario", "手机作为智能家居控制中心，音箱做语音控制入口，手表随身提醒");
            }
            return finish(result, budget);
        }

        if (lower.contains("watchfit") || lower.contains("手表")) {
            result.put("baseProduct", "比特 WatchFit 智能手表");
            if (budget.allows(899)) {
                ObjectNode bundle = bundles.addObject();
                bundle.put("name", "运动伴侣套装");
                addItems(bundle, "比特 WatchFit 智能手表（¥599）",
                        "比特 AirX 真无线耳机（¥399）");
                bundle.put("totalPrice", 998);
                bundle.put("bundlePrice", 899);
                bundle.put("saving", 99);
                bundle.put("scenario", "手表记录运动数据，耳机配合运动听歌");
            }
            return finish(result, budget);
        }

        if (lower.contains("soundbox") || lower.contains("音箱")) {
            result.put("baseProduct", "比特 SoundBox Mini 智能音箱");
            if (budget.allows(1899)) {
                ObjectNode bundle = bundles.addObject();
                bundle.put("name", "语音清扫方案");
                bundle.putArray("ownedItems")
                        .add("比特 SoundBox Mini 智能音箱");
                bundle.putArray("recommendedItems")
                        .add("比特 S11 Pro 扫地机（¥1899）");
                bundle.put("additionalPurchasePrice", 1899);
                bundle.put("soundBoxControlStatus", "SUPPORTED");
                bundle.put("roomLevelVoiceCleaningStatus", "UNKNOWN");
                bundle.put("gatewayRequirementStatus", "UNKNOWN");
                bundle.put("compatibility", "同一比特账号下完成配网和授权后，"
                        + "支持开始清扫、暂停清扫和返回充电；房间级语音清扫和网关要求资料未说明");
                bundle.put("scenario", "复用用户已有音箱作为语音入口");
            }
            return finish(result, budget);
        }

        result.put("baseProduct", baseProduct);
        result.put("status", "NO_BUNDLE_FOR_PRODUCT");
        result.put("message", "暂无该商品的搭配推荐方案");
        return result.toString();
    }

    private Budget parseBudget(String input) {
        try {
            JsonNode budgetNode = MAPPER.readTree(input).get("budget");
            if (budgetNode == null || budgetNode.isNull()) {
                return new Budget(null, true);
            }
            if (!budgetNode.isNumber()) {
                return new Budget(null, false);
            }
            double value = budgetNode.asDouble();
            return new Budget(value, value > 0);
        } catch (Exception e) {
            return new Budget(null, false);
        }
    }

    private void addItems(ObjectNode bundle, String... items) {
        ArrayNode array = bundle.putArray("items");
        for (String item : items) {
            array.add(item);
        }
    }

    private String finish(ObjectNode result, Budget budget) {
        if (result.withArray("bundles").isEmpty()) {
            result.put("status", "NO_MATCH_WITHIN_BUDGET");
            result.put("budget", budget.value());
            result.put("message", "当前没有不超过预算的搭配方案");
        } else {
            result.put("status", "MATCHED");
            if (budget.value() != null) {
                result.put("budget", budget.value());
            }
        }
        return result.toString();
    }

    private record Budget(Double value, boolean valid) {

        boolean allows(double price) {
            return value == null || price <= value;
        }
    }
}
