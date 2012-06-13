package groovy.script

import org.codehaus.groovy.control.CompilePhase

class GroovyScriptController {
  def model
  def view

  // void mvcGroupInit(Map args) {
  //    // this method is called after model and view are injected
  // }

  // void mvcGroupDestroy() {
  //    // this method is called when the group is destroyed
  // }

  void groovyChanged( event ) {
    try {
      def code = new AstNodeToScriptAdapter().compileToScript( model.groovyCode, CompilePhase.SEMANTIC_ANALYSIS.getPhaseNumber(), null, true, false )
      model.jsCode = code
    }
    catch( e ) {

    }
  }
}
