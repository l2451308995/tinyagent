package com.nageoffer.ai.tinyagent.react.tools;

import com.nageoffer.ai.tinyagent.react.Tool;
import com.nageoffer.ai.tinyagent.react.ToolUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 用确定性日期计算保修和七天无理由状态，避免让模型猜测当前日期或自行做边界计算
 */
public class CheckWarrantyTool implements Tool {

    private final Clock clock;

    public CheckWarrantyTool() {
        this(Clock.systemDefaultZone());
    }

    public CheckWarrantyTool(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "checkWarranty";
    }

    @Override
    public String description() {
        return "根据签收日期和已查询到的政策期限，计算当前运行日期下的保修状态与七天无理由状态。";
    }

    @Override
    public String parameters() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "signDate": {
                      "type": "string",
                      "description": "订单签收日期，格式为 yyyy-MM-dd"
                    },
                    "warrantyYears": {
                      "type": "integer",
                      "description": "知识库明确给出的整机保修年数"
                    },
                    "noReasonReturnDays": {
                      "type": "integer",
                      "description": "知识库明确给出的无理由退货自然日数"
                    }
                  },
                  "required": ["signDate", "warrantyYears", "noReasonReturnDays"]
                }""";
    }

    @Override
    public String invoke(String input) {
        String signDateText = ToolUtils.extractRequiredField(input, "signDate");
        String warrantyYearsText = ToolUtils.extractRequiredField(input, "warrantyYears");
        String noReasonDaysText = ToolUtils.extractRequiredField(input, "noReasonReturnDays");
        if (signDateText.isBlank()) {
            return ToolUtils.missingRequiredField("signDate");
        }
        if (warrantyYearsText.isBlank()) {
            return ToolUtils.missingRequiredField("warrantyYears");
        }
        if (noReasonDaysText.isBlank()) {
            return ToolUtils.missingRequiredField("noReasonReturnDays");
        }

        try {
            LocalDate signDate = LocalDate.parse(signDateText);
            int warrantyYears = Integer.parseInt(warrantyYearsText);
            int noReasonDays = Integer.parseInt(noReasonDaysText);
            if (warrantyYears <= 0 || noReasonDays <= 0) {
                return "{\"error\":\"期限参数必须为正整数\"}";
            }

            LocalDate asOfDate = LocalDate.now(clock);
            // “自签收日起 N 年”包含签收日当天，因此截止日是 N 年后的前一天
            LocalDate warrantyEndDate = signDate.plusYears(warrantyYears).minusDays(1);
            LocalDate noReasonReturnEndDate = signDate.plusDays(noReasonDays);

            String warrantyStatus = asOfDate.isAfter(warrantyEndDate)
                    ? "EXPIRED" : "IN_WARRANTY";
            String noReasonReturnStatus = asOfDate.isAfter(noReasonReturnEndDate)
                    ? "EXPIRED" : "AVAILABLE";

            return "{"
                    + "\"asOfDate\":\"" + asOfDate + "\","
                    + "\"signDate\":\"" + signDate + "\","
                    + "\"warrantyEndDate\":\"" + warrantyEndDate + "\","
                    + "\"warrantyBoundary\":\"自签收日起计算，含签收日与到期日当天\","
                    + "\"warrantyStatus\":\"" + warrantyStatus + "\","
                    + "\"noReasonReturnEndDate\":\"" + noReasonReturnEndDate + "\","
                    + "\"noReasonReturnBoundary\":\"签收次日起计算，含截止日当天；商品仍需符合完好条件\","
                    + "\"noReasonReturnStatus\":\"" + noReasonReturnStatus + "\""
                    + "}";
        } catch (DateTimeParseException | NumberFormatException e) {
            return "{\"error\":\"日期必须为 yyyy-MM-dd，期限必须为正整数\"}";
        }
    }
}
