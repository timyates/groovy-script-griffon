application {
    title = 'GroovyScript'
    startupGroups = ['groovy-script']

    // Should Griffon exit when no Griffon created frames are showing?
    autoShutdown = true

    // If you want some non-standard application class, apply it here
    //frameClass = 'javax.swing.JFrame'
}
mvcGroups {
    // MVC Group for "groovy-script"
    'groovy-script' {
        model      = 'groovy.script.GroovyScriptModel'
        view       = 'groovy.script.GroovyScriptView'
        controller = 'groovy.script.GroovyScriptController'
    }

}
