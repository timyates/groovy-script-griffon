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

class AstNodeToScriptVisitor extends PrimaryClassNodeOperation implements GroovyCodeVisitor, GroovyClassVisitor {

    private Writer _out
    Stack<String> classNameStack = new Stack<String>()
    MethodNode currentMethod
    String _indent = ""
    Map varDetails
    boolean readyToIndent = true
    boolean showScriptFreeForm
    boolean scriptHasBeenVisited

    def AstNodeToScriptVisitor(Writer writer, Map varDetails, boolean showScriptFreeForm = true ) {
        this._out = writer;
        this.showScriptFreeForm = showScriptFreeForm
        this.scriptHasBeenVisited = false
        this.varDetails = varDetails
    }

    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        visitPackage(source?.getAST()?.getPackage())

        visitAllImports(source)

        if( showScriptFreeForm && !scriptHasBeenVisited) {
            scriptHasBeenVisited = true
            source?.getAST()?.getStatementBlock()?.visit(this)
        }
        if( !showScriptFreeForm && !classNode.isScript() ) {
            if( varDetails.classes && !scriptHasBeenVisited ) {
                print "var ${varDetails.classes.join( ', ' )} ;"
                printLineBreak()
            }
            if( varDetails.properties && !scriptHasBeenVisited ) {
                print "var ${varDetails.properties.unique().join( ', ' )} ;"
                printLineBreak()
            }
            if( varDetails.needExtends && !scriptHasBeenVisited ) {
                print '''var __extends = function(child, parent) {
                        |    for (var key in parent) {
                        |        if (__hasProp.call(parent, key)) child[key] = parent[key];
                        |    }
                        |    function ctor() { 
                        |      this.constructor = child;
                        |    }
                        |    ctor.prototype = parent.prototype;
                        |    child.prototype = new ctor();
                        |    child.__super__ = parent.prototype;
                        |    return child;
                        |};'''.stripMargin()
                printDoubleBreak()
            }
            scriptHasBeenVisited = true
            visitClass classNode
        }
    }

    private def visitAllImports(SourceUnit source) {
        // Do nothing
    }


    void print(parameter) {
        def output = parameter.toString()

        if (readyToIndent) {
            _out.print _indent
            readyToIndent = false
            while (output.startsWith(' ')) {
                output = output[1..-1]  // trim left
            }
        }
        if (_out.toString().endsWith(' ')) {
            if (output.startsWith(' ')) {
                output = output[1..-1]
            }
        }
        _out.print output
    }

    def println(parameter) {
        throw new UnsupportedOperationException('Wrong API')
    }

    def indented(Closure block) {
        String startingIndent = _indent
        _indent = _indent + "    "
        block()
        _indent = startingIndent
    }

    def printLineBreak() {
        if (!_out.toString().endsWith('\n')) {
            _out.print '\n'
        }
        readyToIndent = true
    }

    def printDoubleBreak() {
        if (_out.toString().endsWith('\n\n')) {
            // do nothing
        } else if (_out.toString().endsWith('\n')) {
            _out.print '\n'
        } else {
            _out.print '\n'
            _out.print '\n'
        }
        readyToIndent = true
    }

    void visitPackage(PackageNode packageNode) {

        if (packageNode) {

            packageNode.annotations?.each {
                visitAnnotationNode(it)
                printLineBreak()
            }

            if (packageNode.text.endsWith(".")) {
                print packageNode.text[0..-2]
            } else {
                print packageNode.text
            }
            printDoubleBreak()
        }
    }

    void visitImport(ImportNode node) {
    }

    @Override
    public void visitClass(ClassNode node) {

        classNameStack.push(node.name)

        print "$node.name = (function(${node.superClass.name!='java.lang.Object'?'_super':''}) {"
        printDoubleBreak()

        indented {
            if( node.superClass.name!='java.lang.Object' ) {
                print "__extends( $node.name, _super )"
                printDoubleBreak()
            }
            node?.properties?.each { visitProperty(it) }
            printLineBreak()
            node?.fields?.each { visitField(it) }
            printDoubleBreak()
            node?.declaredConstructors?.each { visitConstructor(it) }
            printLineBreak()
            node?.methods?.each { visitMethod(it) }
            print "return $node.name ;"
            printDoubleBreak()
        }
        print "})(${node.superClass.name!='java.lang.Object'?node.superClass.name:''});"

        printLineBreak()
        classNameStack.pop()
    }

    private void visitGenerics(GenericsType[] generics) {
    }

    @Override
    public void visitConstructor(ConstructorNode node) {
        visitMethod(node)
    }

    private String visitParameters(parameters) {
        boolean first = true

        parameters.each { Parameter it ->
            if (!first) {
                print ', '
            }
            first = false

            print it.name
            if (it.initialExpression && !(it.initialExpression instanceof EmptyExpression)) {
                print ' = '
                it.initialExpression.visit this
            }
        }
    }

    @Override
    public void visitMethod(MethodNode node) {
        currentMethod = node
        if (node.name == '<init>') {
            print "function ${classNameStack.peek()}("
            visitParameters(node.parameters)
            print ") {"
            printLineBreak()
        } else if (node.name == '<clinit>') {
            print 'static { '
            printLineBreak()
        } else {
            print "${classNameStack.peek()}.prototype.${node.name} = function("
            visitParameters(node.parameters)
            print ") {"
            printLineBreak()
        }

        indented {
            node?.code?.visit(this)
        }
        printLineBreak()
        print '}'
        printDoubleBreak()
        currentMethod = null
    }

    private def visitModifiers(int modifiers) {
    }

    @Override
    public void visitField(FieldNode node) {
    }

    public void visitAnnotationNode(AnnotationNode node) {
    }

    @Override
    public void visitProperty(PropertyNode node) {
        // is a FieldNode, avoid double dispatch
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {
        block?.statements?.eachWithIndex { it, idx ->
            if( idx == block?.statements?.size() - 1 )
              print 'return '
            it.visit(this);
            printLineBreak()
        }
        if (!_out.toString().endsWith('\n')) {
            printLineBreak()
        }
    }

    @Override
    public void visitForLoop(ForStatement statement) {

        print 'for ('
        if (statement?.variable != ForStatement.FOR_LOOP_DUMMY) {
            visitParameters([statement.variable])
            print ' : '
        }

        if (statement?.collectionExpression instanceof ListExpression) {
            statement?.collectionExpression?.visit this
        } else {
            statement?.collectionExpression?.visit this
        }
        print ') {'
        printLineBreak()
        indented {
            statement?.loopBlock?.visit this
        }
        print '}'
        printLineBreak()
    }

    @Override
    public void visitIfElse(IfStatement ifElse) {
        print 'if ('
        ifElse?.booleanExpression?.visit this
        print ') {'
        printLineBreak()
        indented {
            ifElse?.ifBlock?.visit this
        }
        printLineBreak()
        if (ifElse?.elseBlock && !(ifElse.elseBlock instanceof EmptyStatement)) {
            print "} else {"
            printLineBreak()
            indented {
                ifElse?.elseBlock?.visit this
            }
            printLineBreak()
        }
        print '}'
        printLineBreak()
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        statement.expression.visit this
    }

    @Override
    public void visitReturnStatement(ReturnStatement statement) {
        printLineBreak()
        print "return "
        statement.getExpression().visit(this);
        print " ;"
        printLineBreak()
    }

    @Override
    public void visitSwitch(SwitchStatement statement) {
        print 'switch ('
        statement?.expression?.visit this
        print ') {'
        printLineBreak()
        indented {
            statement?.caseStatements?.each {
                visitCaseStatement it
            }
            if (statement?.defaultStatement) {
                print 'default: '
                printLineBreak()
                statement?.defaultStatement?.visit this
            }
        }
        print '}'
        printLineBreak()
    }

    @Override
    public void visitCaseStatement(CaseStatement statement) {
        print 'case '
        statement?.expression?.visit this
        print ':'
        printLineBreak()
        indented {
            statement?.code?.visit this
        }
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
        print 'break'
        printLineBreak()
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
        print 'continue'
        printLineBreak()
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression expression) {
        boolean isSuper = false
        Expression objectExp = expression.getObjectExpression()
        if (objectExp instanceof VariableExpression) {
            if( objectExp.variable == 'super' ) {
                print "${classNameStack.peek()}.__super__."
                isSuper = true
            }
            else {
                visitVariableExpression(objectExp, false)
            }
        } else {
            objectExp.visit(this);
        }
        if( isSuper ) {
            Expression method = expression.getMethod()
            if (method instanceof ConstantExpression) {
                visitConstantExpression(method, true)
            } else {
                method.visit(this);
            }
            print '.call( this, '
            visitArgumentlistExpression expression?.arguments, false, false
            print ' ) ;'
        }
        else {
            if (expression.spreadSafe) {
                print '*'
            }
            if (expression.safe) {
                print '?'
            }
            print '.'
            Expression method = expression.getMethod()
            if (method instanceof ConstantExpression) {
                visitConstantExpression(method, true)
            } else {
                method.visit(this);
            }
            expression.getArguments().visit(this)
        }
    }

    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        def name = expression?.ownerType?.name
        def isSuper = name == 'super'

        print( isSuper ?
                "${classNameStack.peek()}.__super__.${expression?.method}.call(this," :
                expression?.ownerType?.name + "." + expression?.method )
        if (expression?.arguments instanceof VariableExpression || expression?.arguments instanceof MethodCallExpression) {
            print '('
            expression?.arguments?.visit this
            print ')'            
        } else {
            expression?.arguments?.visit this
        }
    }

    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression expression) {
        if (expression?.isSuperCall()) {
            print 'super'
        } else if (expression?.isThisCall()) {
            print 'this '
        } else {
            print 'new '
            visitType expression?.type
        }
        expression?.arguments?.visit this
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        expression?.leftExpression?.visit this
        print " $expression.operation.text "
        expression.rightExpression.visit this

        if (expression?.operation?.text == '[') {
            print ']'
        }
    }

    @Override
    public void visitPostfixExpression(PostfixExpression expression) {
        print '('
        expression?.expression?.visit this
        print ')'
        print expression?.operation?.text
    }

    @Override
    public void visitPrefixExpression(PrefixExpression expression) {
        print expression?.operation?.text
        print '('
        expression?.expression?.visit this
        print ')'
    }


    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        print '{ '
        if (expression?.parameters) {
            visitParameters(expression?.parameters)
            print ' ->'
        }
        printLineBreak()
        indented {
            expression?.code?.visit this
        }
        print '}'
    }

    @Override
    public void visitTupleExpression(TupleExpression expression) {
        print '('
        visitExpressionsAndCommaSeparate(expression?.expressions)
        print ')'
    }

    @Override
    public void visitRangeExpression(RangeExpression expression) {
        print '('
        expression?.from?.visit this
        print '..'
        expression?.to?.visit this
        print ')'
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        expression?.objectExpression?.visit this
        if (expression?.spreadSafe) {
            print '*'
        } else if (expression?.isSafe()) {
            print '?'
        }
        print '.'
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
        print expression?.field?.name
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression, boolean unwrapQuotes = false) {
        if (expression.value instanceof String && !unwrapQuotes) {
            // string reverse escaping is very naive
            def escaped = ((String) expression.value).replaceAll('\n', '\\\\n').replaceAll("'", "\\\\'")
            print "'$escaped'"
        } else {
            print expression.value
        }
    }

    @Override
    public void visitClassExpression(ClassExpression expression) {
        print expression.text
    }

    @Override
    public void visitVariableExpression(VariableExpression expression, boolean spacePad = true) {
        def name = expression.name
        if( varDetails[ classNameStack.join( '.' ) ]?.properties?.contains( name ) &&
           !varDetails[ classNameStack.join( '.' ) ]?.methods?.getAt( "${currentMethod.name}~${currentMethod.parameters*.name.join( '_' )}" )?.contains( name ) ) {
            name = "this.${name}"
        }
        if (spacePad) {
            print ' ' + name + ' '
        } else {
            print name
        }
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        // handle multiple assignment expressions
        if (expression?.leftExpression instanceof ArgumentListExpression) {
            print 'def '
            visitArgumentlistExpression expression?.leftExpression, true
            print " $expression.operation.text "
            expression.rightExpression.visit this

            if (expression?.operation?.text == '[') {
                print ']'
            }
        } else {
            visitBinaryExpression expression // is a BinaryExpression
        }
    }

    @Override
    public void visitGStringExpression(GStringExpression expression) {
        def consts = expression.strings
        def vars   = expression.values
        def all    = [consts,vars].transpose().flatten() + consts[ -1 ]
        boolean first = true
        all.each {
            if( !first ) {
                print ' + '
            }
            it.visit this
            first = false
        }
    }

    @Override
    public void visitSpreadExpression(SpreadExpression expression) {
        print '*'
        expression?.expression?.visit this
    }

    @Override
    public void visitNotExpression(NotExpression expression) {
        print '!('
        expression?.expression?.visit this
        print ')'
    }

    @Override
    public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        print '-('
        expression?.expression?.visit this
        print ')'
    }

    @Override
    public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        print '+('
        expression?.expression?.visit this
        print ')'
    }

    @Override
    public void visitCastExpression(CastExpression expression) {
        print '(('
        expression?.expression?.visit this
        print ') as '
        visitType(expression?.type)
        print ')'

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
            print "${classNode.name[(numDimensions + 1)..-2]}" + ('[]' * numDimensions)
        } else {
            print name
        }
        visitGenerics classNode?.genericsTypes
    }

    @Override
    public void visitArgumentlistExpression(ArgumentListExpression expression, boolean showTypes = false, boolean parenthesize = true ) {
        if( parenthesize ) print '('
        int count = expression?.expressions?.size()
        expression.expressions.each {
            if (showTypes) {
                visitType it.type
                print ' '
            }
            if (it instanceof VariableExpression) {
                visitVariableExpression it, false
            } else if (it instanceof ConstantExpression) {
                visitConstantExpression it, false
            } else {
                it.visit this
            }
            count--
            if (count) print ', '
        }
        if( parenthesize ) print ')'
    }

    @Override
    public void visitBytecodeExpression(BytecodeExpression expression) {
        print "/*BytecodeExpression*/"
        printLineBreak()
    }



    @Override
    public void visitMapExpression(MapExpression expression) {
        print '['
        visitExpressionsAndCommaSeparate(expression?.mapEntryExpressions)
        print ']'
    }

    @Override
    public void visitMapEntryExpression(MapEntryExpression expression) {
        if (expression?.keyExpression instanceof SpreadMapExpression) {
            print '*'            // is this correct? 
        } else {
            expression?.keyExpression?.visit this
        }
        print ': '
        expression?.valueExpression?.visit this
    }

    @Override
    public void visitListExpression(ListExpression expression) {
        print '['
        visitExpressionsAndCommaSeparate(expression?.expressions)
        print ']'
    }

    @Override
    public void visitTryCatchFinally(TryCatchStatement statement) {
        print 'try {'
        printLineBreak()
        indented {
            statement?.tryStatement?.visit this
        }
        printLineBreak()
        print '} '
        printLineBreak()
        statement?.catchStatements?.each { CatchStatement catchStatement ->
            visitCatchStatement(catchStatement)
        }
        print 'finally { '
        printLineBreak()
        indented {
            statement?.finallyStatement?.visit this
        }
        print '} '
        printLineBreak()
    }

    @Override
    public void visitThrowStatement(ThrowStatement statement) {
        print 'throw '
        statement?.expression?.visit this
        printLineBreak()
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        print '/* SYNCHRONIZED SKIPPED */'
        printLineBreak()
        statement?.code?.visit this
    }

    @Override
    public void visitTernaryExpression(TernaryExpression expression) {
        expression?.booleanExpression?.visit this
        print ' ? '
        expression?.trueExpression?.visit this
        print ' : '
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
        print 'while ('
        statement?.booleanExpression?.visit this
        print ') {'
        printLineBreak()
        indented {
            statement?.loopBlock?.visit this
        }
        printLineBreak()
        print '}'
        printLineBreak()
    }

    @Override
    public void visitDoWhileLoop(DoWhileStatement statement) {
        print 'do {'
        printLineBreak()
        indented {
            statement?.loopBlock?.visit this
        }
        print '} while ('
        statement?.booleanExpression?.visit this
        print ')'
        printLineBreak()
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        print 'catch ('
        visitParameters([statement.variable])
        print ') {'
        printLineBreak()
        indented {
            statement.code?.visit this
        }
        print '} '
        printLineBreak()
    }

    @Override
    public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        print '~('
        expression?.expression?.visit this
        print ') '
    }


    @Override
    public void visitAssertStatement(AssertStatement statement) {
        print 'assert '
        statement?.booleanExpression?.visit this
        print ' : '
        statement?.messageExpression?.visit this
    }

    @Override
    public void visitClosureListExpression(ClosureListExpression expression) {
        boolean first = true
        expression?.expressions?.each {
            if (!first) {
                print ';'
            }
            first = false
            it.visit this
        }
    }

    @Override
    public void visitMethodPointerExpression(MethodPointerExpression expression) {
        expression?.expression?.visit this
        print '.&'
        expression?.methodName?.visit this
    }

    @Override
    public void visitArrayExpression(ArrayExpression expression) {
        print 'new '
        visitType expression?.elementType
        print '['
        visitExpressionsAndCommaSeparate(expression?.sizeExpression)
        print ']'
    }

    private void visitExpressionsAndCommaSeparate(List<? super Expression> expressions) {
        boolean first = true
        expressions?.each {
            if (!first) {
                print ', '
            }
            first = false
            it.visit this
        }
    }

    @Override
    public void visitSpreadMapExpression(SpreadMapExpression expression) {
        print '*:'
        expression?.expression?.visit this
    }
}
