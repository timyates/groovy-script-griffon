package groovy.script

import griffon.transform.PropertyListener
import groovy.beans.Bindable

class GroovyScriptModel {
  def controller

  @Bindable
  @PropertyListener({controller.groovyChanged( it )})
  String groovyCode

  @Bindable
  String jsCode
}