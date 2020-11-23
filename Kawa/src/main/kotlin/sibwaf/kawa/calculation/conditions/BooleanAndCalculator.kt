package sibwaf.kawa.calculation.conditions

import sibwaf.kawa.DataFrame
import sibwaf.kawa.MutableDataFrame
import sibwaf.kawa.calculation.ValueCalculatorState
import sibwaf.kawa.constraints.BooleanConstraint
import sibwaf.kawa.utility.flattenExpression
import sibwaf.kawa.values.BooleanValue
import sibwaf.kawa.values.ValueSource
import spoon.reflect.code.BinaryOperatorKind
import spoon.reflect.code.CtBinaryOperator
import spoon.reflect.code.CtExpression

open class BooleanAndCalculator : ConditionCalculator {

    override fun supports(expression: CtExpression<*>) = expression is CtBinaryOperator<*> && expression.kind == BinaryOperatorKind.AND

    override suspend fun calculateCondition(state: ValueCalculatorState, expression: CtExpression<*>): ConditionCalculatorResult {
        expression as CtBinaryOperator<*>

        val operands = flattenExpression(expression, BinaryOperatorKind.AND)

        var thenFrame = state.frame
        val elseFrames = ArrayList<DataFrame>(operands.size)

        val constraints = ArrayList<BooleanConstraint>(operands.size)
        var nextState = state
        for (operand in operands) {
            val (operandThenFrame, operandElseFrame, _, operandConstraint) = nextState.getConditionValue(operand)
            constraints += operandConstraint

            thenFrame = operandThenFrame
            elseFrames += operandElseFrame.compact(state.frame)

            nextState = state.copy(frame = operandThenFrame)
        }

        val elseFrame = DataFrame.merge(state.frame, elseFrames)

        var result = constraints.first()
        for ((index, constraint) in constraints.withIndex()) {
            result = result.and(constraint)

            if (result.isFalse || constraints.subList(0, index).any { constraint.and(it).isFalse }) {
                result = BooleanConstraint.createFalse()
                break
            }
        }

        return ConditionCalculatorResult(
                thenFrame = MutableDataFrame(thenFrame).apply { isReachable = !result.isFalse },
                elseFrame = MutableDataFrame(elseFrame).apply { isReachable = !result.isTrue },
                value = BooleanValue(ValueSource.NONE),
                constraint = result
        )
    }
}