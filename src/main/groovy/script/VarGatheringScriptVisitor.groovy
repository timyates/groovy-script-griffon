package groovy.script

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.control.CompilePhase
import java.lang.reflect.Modifier
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation
import org.codehaus.groovy.classgen.Verifier

class VarGatheringScriptVisitor extends PrimaryClassNodeOperation implements GroovyCodeVisitor, GroovyClassVisitor {

    Stack<String> classNameStack = new Stack<String>();
    boolean showScriptFreeForm
    boolean showScriptClass
    boolean scriptHasBeenVisited
    Map varDetails

    def VarGatheringScriptVisitor(Map varDetails) {
        this.varDetails = varDetails
        this.scriptHasBeenVisited = false
    }

    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if( !scriptHasBeenVisited ) {
            scriptHasBeenVisited = true
            source?.getAST()?.getStatementBlock()?.visit(this)
        }
        if( !classNode.isScript() ) {
            visitClass classNode
        }
    }

    @Override
    public void visitClass(ClassNode node) {

        classNameStack.push(node.name)

        if( !varDetails.needExtends ) {
          varDetails.needExtends = node.superClass.name != 'java.lang.Object'    
        }

        varDetails[ classNameStack.join( '.' ) ] = [ methods:[:] ].withDefault { [] }
        varDetails.classes = ( varDetails.classes ?: [] ) + node.name

        node?.properties?.each { visitProperty(it) }
        node?.fields?.each { visitField(it) }
        node?.declaredConstructors?.each { visitConstructor(it) }
        node?.methods?.each { visitMethod(it) }

        classNameStack.pop()
    }

    @Override
    public void visitConstructor(ConstructorNode node) {
        visitMethod(node)
    }

    private String visitParameters(parameters) {
        boolean first = true

        parameters.each { Parameter it ->
            first = false

            it.annotations?.each {
                visitAnnotationNode(it)
            }

            visitModifiers(it.modifiers)
            visitType it.type
            if (it.initialExpression && !(it.initialExpression instanceof EmptyExpression)) {
                it.initialExpression.visit this
            }
        }
    }

    @Override
    public void visitMethod(MethodNode node) {
        def clazz = classNameStack.join( '.' )
        if( varDetails[ clazz ] ) {
            varDetails[ clazz ].methods[ "${node.name}~${node.parameters*.name.join( '_' )}" ] = node.parameters*.name
        }
        if (node.name == '<init>') {
            visitParameters(node.parameters)
        } else if (node.name == '<clinit>') {
        } else {
            visitType node.returnType
            visitParameters(node.parameters)

            if (node.exceptions) {
                boolean first = true
                node.exceptions.each {
                    first = false
                    visitType it
                }
            }
        }

        node?.code?.visit(this)
    }

    private def visitModifiers(int modifiers) {
    }

    @Override
    public void visitField(FieldNode node) {
        node?.annotations?.each {
            visitAnnotationNode(it)
        }
        visitModifiers(node.modifiers)
        visitType node.type
        // do not print initial expression, as this is executed as part of the constructor, unless on static constant
        Expression exp = node.initialValueExpression
        if (exp instanceof ConstantExpression) exp = Verifier.transformToPrimitiveConstantIfPossible(exp)
        ClassNode type = exp?.type
        if (Modifier.isStatic(node.modifiers) && Modifier.isFinal(node.getModifiers())
                && exp instanceof ConstantExpression
                && type == node.type
                && ClassHelper.isStaticConstantInitializerType(type)) {
            // GROOVY-5150: final constants may be initialized directly
            if (ClassHelper.STRING_TYPE == type) {
            } else if (ClassHelper.char_TYPE == type) {
            } else {
            }
        }
    }

    public void visitAnnotationNode(AnnotationNode node) {
        if (node?.members) {
            boolean first = true
            node.members.each { String name, Expression value ->
                first = false
                value.visit(this)
            }
        }
    }

    @Override
    public void visitProperty(PropertyNode node) {
        // is a FieldNode, avoid double dispatch
        varDetails[ classNameStack.join( '.' ) ].properties << node.name
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {
        block?.statements?.each {
            it.visit(this);
        }
    }

    @Override
    public void visitForLoop(ForStatement statement) {

        if (statement?.variable != ForStatement.FOR_LOOP_DUMMY) {
            visitParameters([statement.variable])
        }

        if (statement?.collectionExpression instanceof ListExpression) {
            statement?.collectionExpression?.visit this
        } else {
            statement?.collectionExpression?.visit this
        }
        statement?.loopBlock?.visit this
    }

    @Override
    public void visitIfElse(IfStatement ifElse) {
        ifElse?.booleanExpression?.visit this
        ifElse?.ifBlock?.visit this
        if (ifElse?.elseBlock && !(ifElse.elseBlock instanceof EmptyStatement)) {
            ifElse?.elseBlock?.visit this
        }
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        statement.expression.visit this
    }

    @Override
    public void visitReturnStatement(ReturnStatement statement) {
        statement.getExpression().visit(this);
    }

    @Override
    public void visitSwitch(SwitchStatement statement) {
        statement?.expression?.visit this
        statement?.caseStatements?.each {
            visitCaseStatement it
        }
        if (statement?.defaultStatement) {
            statement?.defaultStatement?.visit this
        }
    }

    @Override
    public void visitCaseStatement(CaseStatement statement) {
        statement?.expression?.visit this
        statement?.code?.visit this
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression expression) {

        Expression objectExp = expression.getObjectExpression()
        if (objectExp instanceof VariableExpression) {
            visitVariableExpression(objectExp, false)
        } else {
            objectExp.visit(this);
        }
        if (expression.spreadSafe) {
        }
        if (expression.safe) {
        }
        Expression method = expression.getMethod()
        if (method instanceof ConstantExpression) {
            visitConstantExpression(method, true)
        } else {
            method.visit(this);
        }
        expression.getArguments().visit(this)
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        if (expression?.arguments instanceof VariableExpression || expression?.arguments instanceof MethodCallExpression) {
            expression?.arguments?.visit this
        } else {
            expression?.arguments?.visit this
        }
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression expression) {
        visitType expression?.type
        expression?.arguments?.visit this
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        expression?.leftExpression?.visit this
        expression.rightExpression.visit this
    }

    @Override
    public void visitPostfixExpression(PostfixExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitPrefixExpression(PrefixExpression expression) {
        expression?.expression?.visit this
    }


    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (expression?.parameters) {
            visitParameters(expression?.parameters)
        }
        expression?.code?.visit this
    }

    @Override
    public void visitTupleExpression(TupleExpression expression) {
        visitExpressionsAndCommaSeparate(expression?.expressions)
    }

    @Override
    public void visitRangeExpression(RangeExpression expression) {
        expression?.from?.visit this
        expression?.to?.visit this
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        expression?.objectExpression?.visit this
        if (expression?.spreadSafe) {
        } else if (expression?.isSafe()) {
        }
        if (expression?.property instanceof ConstantExpression) {
            visitConstantExpression(expression?.property, true)
        } else {
            expression?.property?.visit this
        }
    }

    @Override
    public void visitAttributeExpression(AttributeExpression attributeExpression) {
        visitPropertyExpression attributeExpression
    }

    @Override
    public void visitFieldExpression(FieldExpression expression) {
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression, boolean unwrapQuotes = false) {
        if (expression.value instanceof String && !unwrapQuotes) {
            // string reverse escaping is very naive
            def escaped = ((String) expression.value).replaceAll('\n', '\\\\n').replaceAll("'", "\\\\'")
        } else {
        }
    }

    @Override
    public void visitClassExpression(ClassExpression expression) {
    }

    @Override
    public void visitVariableExpression(VariableExpression expression, boolean spacePad = true) {
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        // handle multiple assignment expressions
        if (expression?.leftExpression instanceof ArgumentListExpression) {
            expression?.leftExpression?.expressions?.each {
                varDetails.properties << it?.name
            }
            visitArgumentlistExpression expression?.leftExpression, true
            expression.rightExpression.visit this
        } else {
            varDetails.properties << expression?.leftExpression?.name
            visitType expression?.leftExpression?.type
            visitBinaryExpression expression // is a BinaryExpression
        }
    }

    @Override
    public void visitGStringExpression(GStringExpression expression) {
    }

    @Override
    public void visitSpreadExpression(SpreadExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitNotExpression(NotExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitCastExpression(CastExpression expression) {
        expression?.expression?.visit this
        visitType(expression?.type)
    }

    /**
     * Prints out the type, safely handling arrays.
     * @param classNode
     *      classnode
     */
    public void visitType(ClassNode classNode) {
        def name = classNode.name
        if (name =~ /^\[+L/ && name.endsWith(";")) {
            int numDimensions = name.indexOf('L')
        } else {
        }
    }

    @Override
    public void visitArgumentlistExpression(ArgumentListExpression expression, boolean showTypes = false) {
        int count = expression?.expressions?.size()
        expression.expressions.each {
            if (showTypes) {
                visitType it.type
            }
            if (it instanceof VariableExpression) {
                visitVariableExpression it, false
            } else if (it instanceof ConstantExpression) {
                visitConstantExpression it, false
            } else {
                it.visit this
            }
        }
    }

    @Override
    public void visitBytecodeExpression(BytecodeExpression expression) {
    }



    @Override
    public void visitMapExpression(MapExpression expression) {
        visitExpressionsAndCommaSeparate(expression?.mapEntryExpressions)
    }

    @Override
    public void visitMapEntryExpression(MapEntryExpression expression) {
        if (expression?.keyExpression instanceof SpreadMapExpression) {
        } else {
            expression?.keyExpression?.visit this
        }
        expression?.valueExpression?.visit this
    }

    @Override
    public void visitListExpression(ListExpression expression) {
        visitExpressionsAndCommaSeparate(expression?.expressions)
    }

    @Override
    public void visitTryCatchFinally(TryCatchStatement statement) {
        statement?.tryStatement?.visit this
        statement?.catchStatements?.each { CatchStatement catchStatement ->
            visitCatchStatement(catchStatement)
        }
        statement?.finallyStatement?.visit this
    }

    @Override
    public void visitThrowStatement(ThrowStatement statement) {
        statement?.expression?.visit this
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        statement?.expression?.visit this
        statement?.code?.visit this
    }

    @Override
    public void visitTernaryExpression(TernaryExpression expression) {
        expression?.booleanExpression?.visit this
        expression?.trueExpression?.visit this
        expression?.falseExpression?.visit this
    }

    @Override
    public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        visitTernaryExpression(expression)
    }

    @Override
    public void visitBooleanExpression(BooleanExpression expression) {
        expression?.expression?.visit this
    }

    @Override
    public void visitWhileLoop(WhileStatement statement) {
        statement?.booleanExpression?.visit this
        statement?.loopBlock?.visit this
    }

    @Override
    public void visitDoWhileLoop(DoWhileStatement statement) {
        statement?.loopBlock?.visit this
        statement?.booleanExpression?.visit this
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        visitParameters([statement.variable])
        statement.code?.visit this
    }

    @Override
    public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        expression?.expression?.visit this
    }


    @Override
    public void visitAssertStatement(AssertStatement statement) {
        statement?.booleanExpression?.visit this
        statement?.messageExpression?.visit this
    }

    @Override
    public void visitClosureListExpression(ClosureListExpression expression) {
        boolean first = true
        expression?.expressions?.each {
            first = false
            it.visit this
        }
    }

    @Override
    public void visitMethodPointerExpression(MethodPointerExpression expression) {
        expression?.expression?.visit this
        expression?.methodName?.visit this
    }

    @Override
    public void visitArrayExpression(ArrayExpression expression) {
        visitType expression?.elementType
        visitExpressionsAndCommaSeparate(expression?.sizeExpression)
    }

    private void visitExpressionsAndCommaSeparate(List<? super Expression> expressions) {
        boolean first = true
        expressions?.each {
            first = false
            it.visit this
        }
    }

    @Override
    public void visitSpreadMapExpression(SpreadMapExpression expression) {
        expression?.expression?.visit this
    }
}
