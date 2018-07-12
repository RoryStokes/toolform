package au.com.agiledigital.toolform.command.build.docker

import au.com.agiledigital.toolform.command.build.BuildEnvironment
import au.com.agiledigital.toolform.model.BuilderConfig
import scala.concurrent.ExecutionContext.Implicits.global
import tugboat.Docker

class DockerBuildEnvironment extends BuildEnvironment {
  val docker = Docker

  override def initialiseBuilder(builderConfig: BuilderConfig) = {
    docker.pull(builderConfig.image)

    val containerConfig = ContainerConfig.builder
      .image(builderConfig.image)
      .cmd("tail", "-f", "/dev/null")
      .build

    val creation    = docker.createContainer(containerConfig, builderConfig.containerName)
    val containerId = creation.id

    docker.startContainer(containerId)
  }

  override def cleanupBuilder(builderConfig: BuilderConfig) =
    docker.listContainers(ListContainersParam.filter("name", builderConfig.containerName)).forEach { container =>
      println(container.id)
      docker.killContainer(container.id)
      docker.removeContainer(container.id)
      println("removed")
    }
}
