package groovy.script

import org.fife.ui.rtextarea.*
import org.fife.ui.rsyntaxtextarea.*

application(title: 'groovy-script',
  preferredSize: [800, 600],
  pack: true,
  locationByPlatform:true,
  iconImage: imageIcon('/griffon-icon-48x48.png').image,
  iconImages: [imageIcon('/griffon-icon-48x48.png').image,
               imageIcon('/griffon-icon-32x32.png').image,
               imageIcon('/griffon-icon-16x16.png').image]) {
    def (inputArea,inputEditor) = new RSyntaxTextArea( 20, 60 ).with { ine ->
      syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_GROOVY
      antiAliasingEnabled = true
      new RTextScrollPane( ine ).with {
        [it,ine]
      }
    }
    def (outputArea,outputEditor) = new RSyntaxTextArea( 20, 60 ).with { oute ->
      syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
      antiAliasingEnabled = true
      editable = false
      new RTextScrollPane( oute ).with {
        [it,oute]
      }
    }
    splitPane( orientation:JSplitPane.HORIZONTAL_SPLIT, resizeWeight:0.5, dividerLocation:-1 ) {
      widget( inputArea )
      widget( outputArea )
    }
    bind( source:inputEditor, sourceProperty:'text', target:model, targetProperty:'groovyCode' )
    bind( source:model, sourceProperty:'jsCode', target:outputEditor, targetProperty:'text' )
}
