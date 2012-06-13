/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * This class takes Groovy source code, compiles it to a specific compile phase, and then decompiles it
 * back to the groovy source. It is used by GroovyConsole's AST Browser, but can also be invoked from
 * the command line.
 *
 * @author Hamlet D'Arcy
 */
public class AstNodeToScriptAdapter  {

    /**
    * This method takes source code, compiles it, then reverses it back to source. 
    * @param script
    *    the source code to be compiled. If invalid, a compile error occurs
    * @param compilePhase
    *    the CompilePhase. Must be an int mapped in @{link CompilePhase}
    * @param classLoader
    *    (optional) the classloader to use. If missing/null then the current is used.    
    *    This parameter enables things like ASTBrowser to invoke this with the correct classpath
    * @param showScriptFreeForm
    *    Whether or not to show the script portion of the source code
    * @param showScriptClass
    *    Whether or not to show the Script class from the source code
    * @returns the source code from the AST state
    */
    public String compileToScript( String script, int compilePhase, ClassLoader classLoader = null ) {

        def writer = new StringWriter()

        classLoader = classLoader ?: new GroovyClassLoader(getClass().classLoader)
        def scriptName = "script" + System.currentTimeMillis() + ".groovy"
        GroovyCodeSource codeSource = new GroovyCodeSource(script, scriptName, "/groovy/script")

        // Phase 1, collect var information
        try {
            def varDetails = [properties:[]]
            CompilationUnit cu = new CompilationUnit( CompilerConfiguration.DEFAULT, codeSource.codeSource, classLoader )
            cu.addPhaseOperation( new VarGatheringScriptVisitor( varDetails ), compilePhase )
            cu.addPhaseOperation( new AstNodeToScriptVisitor( writer, varDetails, false ), compilePhase )
            cu.addPhaseOperation( new AstNodeToScriptVisitor( writer, varDetails, true ), compilePhase )
            cu.addSource( codeSource.getName(), script )
            cu.compile( compilePhase )
System.out.println( varDetails )
        } catch (CompilationFailedException cfe) {
            writer.println "Unable to produce AST for this phase due to earlier compilation error:"
            cfe.message.eachLine {
                writer.println it
            }
            writer.println "Fix the above error(s) and then press Refresh"
        } catch ( t ) {
            writer.println "Unable to produce AST for this phase due to an error:"
            writer.println t.getMessage()
            writer.println "Fix the above error(s) and then press Refresh"
        }
        return writer.toString()
    }
}