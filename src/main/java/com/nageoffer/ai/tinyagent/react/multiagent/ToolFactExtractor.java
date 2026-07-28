package com.nageoffer.ai.tinyagent.react.multiagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.tinyagent.react.ReActAgent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * facts 的确定性生产者：只从工具返回的 JSON 里取字段，模型说了什么完全不参与
 * <p>
 * 这是整个交接链路里最容易被跳过、也最不能跳过的一环。如果 facts 由模型自己总结，
 * 下游拿到的仍然是一段可能被改写过的自然语言，所谓“结构化交接”就只剩一个 Map 的壳
 * 规则表本身就是领域层和通信层之间的契约，它应该和工具的输出 schema 一起版本化
 */
public final class ToolFactExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 一条抽取规则：从 toolName 的返回值里取 fieldPath，落成名为 factKey 的交接字段
     * fieldPath 支持用点号下钻嵌套对象和数组下标，例如 productA.name、bundles.0.soundBoxControlStatus
     */
    public record Rule(String toolName, String fieldPath, String factKey) {

        public Rule {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("toolName 不能为空");
            }
            if (fieldPath == null || fieldPath.isBlank()) {
                throw new IllegalArgumentException("fieldPath 不能为空");
            }
            if (factKey == null || factKey.isBlank()) {
                throw new IllegalArgumentException("factKey 不能为空");
            }
            toolName = toolName.strip();
            fieldPath = fieldPath.strip();
            factKey = factKey.strip();
        }
    }

    private final List<Rule> rules;

    public ToolFactExtractor(List<Rule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * 便捷写法：按 toolName、fieldPath、factKey 三元组连续声明
     */
    public static ToolFactExtractor of(String... triples) {
        if (triples.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "规则必须按 toolName、fieldPath、factKey 三个一组声明");
        }
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < triples.length; i += 3) {
            rules.add(new Rule(triples[i], triples[i + 1], triples[i + 2]));
        }
        return new ToolFactExtractor(rules);
    }

    /**
     * 按本轮真实发生的工具调用顺序抽取
     * <p>
     * 带 error 字段的返回整条跳过：失败的调用不产生事实
     * 同一个 factKey 被多次命中时后写覆盖先写，因为后一次调用通常是模型纠正参数后的重试
     */
    public Map<String, String> extract(List<ReActAgent.ToolObservation> observations) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (observations == null || observations.isEmpty() || rules.isEmpty()) {
            return facts;
        }

        for (ReActAgent.ToolObservation observation : observations) {
            JsonNode output = parse(observation.output());
            if (output == null || output.hasNonNull("error")) {
                continue;
            }
            for (Rule rule : rules) {
                if (!rule.toolName().equals(observation.toolName())) {
                    continue;
                }
                String value = readPath(output, rule.fieldPath());
                if (!value.isBlank()) {
                    facts.put(rule.factKey(), value);
                }
            }
        }
        return facts;
    }

    private JsonNode parse(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(output.strip());
            return node.isObject() ? node : null;
        } catch (Exception ignored) {
            // 工具没返回 JSON 就没有可抽取的结构化事实，交给 summary 兜底
            return null;
        }
    }

    private String readPath(JsonNode root, String fieldPath) {
        JsonNode current = root;
        for (String segment : fieldPath.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return "";
            }
            current = current.isArray()
                    ? current.path(parseIndex(segment))
                    : current.path(segment);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return "";
        }
        if (current.isValueNode()) {
            return current.asText().strip();
        }
        // 命中的是对象或数组，说明规则写得太浅，直接原样带过去比丢掉更有用
        return current.toString();
    }

    private int parseIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
