package groovy.script

import griffon.transform.PropertyListener
import groovy.beans.Bindable

class GroovyScriptModel {
  def controller

  @Bindable
  @PropertyListener({controller.groovyChanged( it )})
  String groovyCode = '''class Animal {
                        |    String name
                        |    Animal( name ) {
                        |        this.name = name
                        |    }

                        |    def move( meters ) {
                        |        console.log "$name moved ${meters}m"
                        |    }
                        |}
                        |
                        |class Snake extends Animal {
                        |    def move() {
                        |        console.log "Slithering..."
                        |        super.move 5
                        |    }
                        |}
                        |
                        |class Horse extends Animal {
                        |    def move() {
                        |        console.log "Galloping..."
                        |        super.move 45
                        |    }
                        |}
                        |
                        |def mrEd = new Horse( 'Mr Ed.' )
                        |def sid  = new Snake( 'Hissing Sid' )
                        |mrEd.move()
                        |sid.move()'''.stripMargin()

  @Bindable
  String jsCode
}