package com.fbint.collector.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Per-question logic rule. Conditions form a recursive tree (group OR single). Actions are
 * applied in order when the rule matches. Source: Formbricks `packages/types/surveys/logic.ts`
 * + `types.ts` deprecated v1 shapes.
 *
 * Conditions are intentionally typed loosely (Map<String, Any?>) because the right operand can
 * be either a static literal or another dynamic field reference; the engine inspects keys at
 * evaluation time rather than us trying to model a discriminated union in Moshi.
 */
@JsonClass(generateAdapter = true)
data class LogicRuleDto(
    val id: String,
    val conditions: ConditionGroupDto,
    val actions: List<LogicActionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ConditionGroupDto(
    val id: String? = null,
    val connector: String = "and",
    val conditions: List<ConditionNodeDto> = emptyList(),
)

/**
 * Node in a condition tree. Either a nested group (children non-empty) or a leaf condition
 * (operator non-null). The parser distinguishes by which fields are present on the JSON object.
 */
@JsonClass(generateAdapter = true)
data class ConditionNodeDto(
    val id: String? = null,
    // group fields
    val connector: String? = null,
    val conditions: List<ConditionNodeDto>? = null,
    // single-condition fields
    val leftOperand: OperandDto? = null,
    val operator: String? = null,
    val rightOperand: OperandDto? = null,
)

@JsonClass(generateAdapter = true)
data class OperandDto(
    val type: String,             // "static" | "question" | "element" | "variable" | "hiddenField"
    val value: Any? = null,       // string | number | string[]
    val meta: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class LogicActionDto(
    val id: String? = null,
    val objective: String,        // "jumpToQuestion" | "requireAnswer" | "calculate"
    val target: String? = null,
    val variableId: String? = null,
    val operator: String? = null, // calculate ops: assign, concat, add, subtract, multiply, divide
    val value: OperandDto? = null,
)
