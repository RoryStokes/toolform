package au.com.agiledigital.toolform.command.build

import au.com.agiledigital.toolform.model.{BuilderConfig, Component, Project}

trait BuildEnvironment {

  def configForComponent(component: Component, project: Project) = BuilderConfig(
    image = s"rorystokes/${component.builder}-tfbuilder",
    containerName = s"${project.id}-${component.id}-tfbuilder"
  )

  def initialiseBuilder(builderConfig: BuilderConfig): Unit

  def initialiseBuilder(component: Component, project: Project): Unit =
    initialiseBuilder(configForComponent(component, project))

  def cleanupBuilder(builderConfig: BuilderConfig): Unit

  def cleanupBuilder(component: Component, project: Project): Unit =
    initialiseBuilder(configForComponent(component, project))
}
