//plugins {
//    java
//}
//
//idePluginDependency {
//    apply<JavaPlugin>()
//
//    publish()
//
//    val jar: Jar by tasks
//
//    fun Jar.populateWith(projectName: String, taskName: String, into: String? = null) {
//        dependsOn("$projectName:$taskName")
//        from(project(projectName).tasks.getByName(taskName).outputs.files) {
//            into?.let { into(it) }
//        }
//    }
//
//    jar.apply {
//        populateWith(":prepare:ide-plugin-dependencies:kotlin-jps-common-for-ide", "jar")
//        populateWith(":kotlin-compiler", "distKotlinc", into = "kotlinc") // PathUtil.HOME_FOLDER_NAME
//        populateWith(":jps-plugin", "jar")
//        populateWith(":prepare:ide-plugin-dependencies:compiler-components-for-jps", "jar")
////        populateWith(":prepare:ide-plugin-dependencies:kotlin-compiler-for-ide", "jar")
//        populateWith(":kotlin-reflect", "jar")
//    }
//}


idePluginDependency {
    @Suppress("UNCHECKED_CAST")
    val compilerComponents = rootProject.extra["compilerModulesForJps"] as List<String>

    val otherProjects = listOf(":kotlin-daemon-client", ":jps-plugin", ":idea:idea-jps-common", ":kotlin-reflect")

    publishProjectJars(compilerComponents + otherProjects, libraryDependencies = listOf(protobufFull()))
}
