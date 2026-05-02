package com.fbint.collector.domain

import com.fbint.collector.data.remote.dto.ConditionGroupDto
import com.fbint.collector.data.remote.dto.ConditionNodeDto
import com.fbint.collector.data.remote.dto.LogicActionDto
import com.fbint.collector.data.remote.dto.OperandDto
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.data.remote.dto.SurveyDto

/**
 * In-memory state used by the logic engine. Variables are mutable because `calculate` actions
 * write to them; answers / hiddenFields are mutated by the runner via [LogicContext.update].
 */
class LogicContext(
    initialAnswers: Map<String, Any?> = emptyMap(),
    initialVariables: Map<String, Any?> = emptyMap(),
    initialHiddenFields: Map<String, Any?> = emptyMap(),
) {
    val answers: MutableMap<String, Any?> = initialAnswers.toMutableMap()
    val variables: MutableMap<String, Any?> = initialVariables.toMutableMap()
    val hiddenFields: MutableMap<String, Any?> = initialHiddenFields.toMutableMap()
}

/**
 * Per-evaluation scratch space carrying the survey + the question whose `logic[]` we're walking.
 * Needed because comparisons against choice-style answers must translate between choice IDs (used
 * in the survey editor's static operands) and choice labels (used in the stored answer).
 */
private data class EvalScope(val survey: com.fbint.collector.data.remote.dto.SurveyDto, val current: com.fbint.collector.data.remote.dto.QuestionDto)

sealed interface NextStep {
    data class Question(val id: String) : NextStep
    data class Ending(val id: String) : NextStep
    data object Done : NextStep
}

/**
 * Decides where to go after the user answers a question. Order:
 *  1. Walk question.logic[] top-down. The first rule whose condition tree evaluates true wins.
 *     Any `calculate` actions on that rule mutate variables; the rule's first `jumpToQuestion`
 *     action determines the target.
 *  2. If no rule matched, fall back to question.logicFallback if set.
 *  3. Otherwise advance to the next question in declaration order, or to the first ending if
 *     this was the last question.
 *
 * `jumpToQuestion.target` is overloaded: it may name a question or an ending. We resolve
 * against questions first, then endings (matches Formbricks server behaviour).
 */
class LogicEngine {

    fun nextStep(
        currentQuestion: QuestionDto,
        survey: SurveyDto,
        ctx: LogicContext,
    ): NextStep {
        val scope = EvalScope(survey, currentQuestion)
        val rules = currentQuestion.logic.orEmpty()
        for (rule in rules) {
            if (!evaluateGroup(rule.conditions, ctx, scope)) continue
            applyActions(rule.actions, ctx, scope)
            val jumpTarget = rule.actions.firstOrNull { it.objective == JUMP }?.target
            if (jumpTarget != null) return resolveTarget(jumpTarget, survey)
        }
        currentQuestion.logicFallback?.takeIf { it.isNotBlank() }?.let { return resolveTarget(it, survey) }

        val idx = survey.questions.indexOfFirst { it.id == currentQuestion.id }
        val next = survey.questions.getOrNull(idx + 1)
        return when {
            next != null -> NextStep.Question(next.id)
            survey.endings.isNotEmpty() -> NextStep.Ending(survey.endings.first().id)
            else -> NextStep.Done
        }
    }

    private fun resolveTarget(target: String, survey: SurveyDto): NextStep {
        if (survey.questions.any { it.id == target }) return NextStep.Question(target)
        if (survey.endings.any { it.id == target }) return NextStep.Ending(target)
        return NextStep.Done
    }

    private fun applyActions(actions: List<LogicActionDto>, ctx: LogicContext, scope: EvalScope) {
        for (a in actions) if (a.objective == CALCULATE) applyCalculate(a, ctx, scope)
    }

    private fun applyCalculate(action: LogicActionDto, ctx: LogicContext, scope: EvalScope) {
        val varId = action.variableId ?: return
        val current = ctx.variables[varId]
        val rhs = resolveOperand(action.value, ctx, scope)
        val updated: Any? = when (action.operator) {
            "assign" -> rhs
            "concat" -> "${current ?: ""}${rhs ?: ""}"
            "add" -> asDouble(current) + asDouble(rhs)
            "subtract" -> asDouble(current) - asDouble(rhs)
            "multiply" -> asDouble(current) * asDouble(rhs)
            "divide" -> {
                val d = asDouble(rhs)
                if (d == 0.0) current else asDouble(current) / d
            }
            else -> current
        }
        ctx.variables[varId] = updated
    }

    private fun evaluateGroup(group: ConditionGroupDto, ctx: LogicContext, scope: EvalScope): Boolean {
        val children = group.conditions
        if (children.isEmpty()) return true
        val results = children.map { evaluateNode(it, ctx, scope) }
        return if (group.connector.equals("or", ignoreCase = true)) results.any { it } else results.all { it }
    }

    private fun evaluateNode(node: ConditionNodeDto, ctx: LogicContext, scope: EvalScope): Boolean {
        if (node.connector != null && node.conditions != null) {
            return evaluateGroup(
                ConditionGroupDto(id = node.id, connector = node.connector, conditions = node.conditions),
                ctx,
                scope,
            )
        }
        val op = node.operator ?: return false
        val left = resolveOperand(node.leftOperand, ctx, scope)
        val right = node.rightOperand?.let { resolveOperand(it, ctx, scope) }
        return applyOperator(op, left, right)
    }

    /**
     * For choice questions, the answer stored is the localized label (HTML-stripped), while
     * the editor's static operands reference choice IDs. Walk the question's choices, strip
     * HTML on each language variant, and return the matching choice's ID. Falls back to the
     * raw answer if nothing matches (e.g. for non-choice questions or open-text answers).
     */
    private fun translateAnswerLabelToId(answer: Any?, choices: List<com.fbint.collector.data.remote.dto.ChoiceDto>): Any? {
        if (choices.isEmpty()) return answer
        return when (answer) {
            is String -> findChoiceIdByLabel(answer, choices) ?: answer
            is List<*> -> answer.map { item ->
                if (item is String) findChoiceIdByLabel(item, choices) ?: item else item
            }
            else -> answer
        }
    }

    private fun findChoiceIdByLabel(label: String, choices: List<com.fbint.collector.data.remote.dto.ChoiceDto>): String? {
        val needle = stripHtmlForCompare(label)
        return choices.firstOrNull { choice ->
            val labels = choice.label?.values.orEmpty()
            labels.any { v -> stripHtmlForCompare(v).equals(needle, ignoreCase = true) }
        }?.id
    }

    private fun stripHtmlForCompare(s: String): String =
        s.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .trim()

    /**
     * Operand resolution. `meta.row` and `meta.field` allow logic to inspect a sub-field of a
     * matrix / address / contactInfo answer (e.g. `{ row: <rowId> }` -> picks the column label
     * for that row in a matrix answer; `{ field: "1" }` -> picks index 1 of an address array).
     */
    private fun resolveOperand(op: OperandDto?, ctx: LogicContext, scope: EvalScope): Any? {
        if (op == null) return null
        return when (op.type) {
            "static" -> op.value
            "question", "element" -> {
                val id = op.value as? String ?: return null
                val raw = ctx.answers[id] ?: return null
                val refQ = scope.survey.questions.firstOrNull { it.id == id }
                val choiceTranslated = if (refQ?.choices != null) {
                    translateAnswerLabelToId(raw, refQ.choices)
                } else raw
                val rowKey = op.meta?.get("row")
                val fieldKey = op.meta?.get("field")
                when {
                    rowKey != null && choiceTranslated is Map<*, *> -> choiceTranslated[rowKey]
                    fieldKey != null && choiceTranslated is List<*> -> choiceTranslated.getOrNull(fieldKey.toIntOrNull() ?: -1)
                    fieldKey != null && choiceTranslated is Map<*, *> -> choiceTranslated[fieldKey]
                    else -> choiceTranslated
                }
            }
            "variable" -> ctx.variables[op.value as? String]
            "hiddenField" -> ctx.hiddenFields[op.value as? String]
            else -> null
        }
    }

    private fun applyOperator(op: String, left: Any?, right: Any?): Boolean {
        // Unary operators
        when (op) {
            "isSubmitted", "isPartiallySubmitted", "isCompletelySubmitted" -> return isPresent(left)
            "isSkipped", "isEmpty" -> return !isPresent(left)
            "isClicked" -> return left == "clicked"
            "isNotClicked" -> return left != "clicked"
            "isAccepted" -> return left == "accepted"
            "isBooked" -> return left == "booked"
            "isSet", "isNotEmpty" -> return left != null && isPresent(left)
            "isNotSet" -> return left == null
        }
        if (right == null) return false

        return when (op) {
            "equals" -> normalize(left) == normalize(right)
            "doesNotEqual" -> normalize(left) != normalize(right)
            "contains" -> stringContains(left, right)
            "doesNotContain" -> !stringContains(left, right)
            "startsWith" -> (left as? String)?.startsWith(right.toString(), ignoreCase = true) == true
            "doesNotStartWith" -> (left as? String)?.startsWith(right.toString(), ignoreCase = true) != true
            "endsWith" -> (left as? String)?.endsWith(right.toString(), ignoreCase = true) == true
            "doesNotEndWith" -> (left as? String)?.endsWith(right.toString(), ignoreCase = true) != true
            "isGreaterThan" -> compareNum(left, right)?.let { it > 0 } == true
            "isLessThan" -> compareNum(left, right)?.let { it < 0 } == true
            "isGreaterThanOrEqual" -> compareNum(left, right)?.let { it >= 0 } == true
            "isLessThanOrEqual" -> compareNum(left, right)?.let { it <= 0 } == true
            "equalsOneOf", "isAnyOf" -> asList(right).any { normalize(left) == normalize(it) }
            "includesAllOf" -> asList(right).map { normalize(it) }.let { wanted ->
                asList(left).map { normalize(it) }.containsAll(wanted)
            }
            "includesOneOf" -> {
                val l = asList(left).map { normalize(it) }.toSet()
                asList(right).any { normalize(it) in l }
            }
            "doesNotIncludeOneOf" -> !applyOperator("includesOneOf", left, right)
            "doesNotIncludeAllOf" -> !applyOperator("includesAllOf", left, right)
            "isBefore" -> normalize(left) < normalize(right)   // ISO 8601 compares lexically
            "isAfter" -> normalize(left) > normalize(right)
            else -> false
        }
    }

    private fun stringContains(left: Any?, right: Any?): Boolean = when (left) {
        is String -> left.contains(right.toString(), ignoreCase = true)
        is Collection<*> -> left.any { normalize(it).equals(normalize(right), ignoreCase = true) }
        else -> false
    }

    private fun isPresent(v: Any?): Boolean = when (v) {
        null -> false
        is String -> v.isNotEmpty()
        is Collection<*> -> v.isNotEmpty()
        is Map<*, *> -> v.isNotEmpty()
        else -> true
    }

    private fun normalize(v: Any?): String = when (v) {
        null -> ""
        is Number -> {
            val d = v.toDouble()
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        else -> v.toString()
    }

    private fun asList(v: Any?): List<Any?> = when (v) {
        null -> emptyList()
        is List<*> -> v
        is Set<*> -> v.toList()
        else -> listOf(v)
    }

    private fun compareNum(left: Any?, right: Any?): Int? {
        val l = (left as? Number)?.toDouble() ?: left?.toString()?.toDoubleOrNull() ?: return null
        val r = (right as? Number)?.toDouble() ?: right?.toString()?.toDoubleOrNull() ?: return null
        return l.compareTo(r)
    }

    private fun asDouble(v: Any?): Double = (v as? Number)?.toDouble() ?: v?.toString()?.toDoubleOrNull() ?: 0.0

    private companion object {
        const val JUMP = "jumpToQuestion"
        const val CALCULATE = "calculate"
    }
}
