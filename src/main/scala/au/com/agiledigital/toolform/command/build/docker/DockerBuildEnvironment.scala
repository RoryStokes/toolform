package au.com.agiledigital.toolform.command.build.docker

import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.command.build.BuildEnvironment
import au.com.agiledigital.toolform.model.BuilderConfig
import cats.data.NonEmptyList
import com.spotify.docker.client.{DefaultDockerClient, DockerClient, LogStream}
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.{Container, ContainerConfig, HostConfig}

import scala.collection.JavaConverters._
import cats.implicits._
import com.spotify.docker.client.messages.HostConfig.Bind

import scala.util.Try

class DockerBuildEnvironment extends BuildEnvironment {

  def withDocker[A](inner: DockerClient => Either[NonEmptyList[ToolFormError], A]): Either[NonEmptyList[ToolFormError], A] = {
    val docker = DefaultDockerClient.fromEnv.build
    val result = Try(inner(docker))
    docker.close()
    result.toEither.leftMap { e =>
      NonEmptyList.of(ToolFormError(s"Failed to execute Docker commands: [${e.getMessage}]"))
    }.joinRight
  }

  def bindPath(path: Path, target: String) =
    Bind
      .from(path.toFile.getAbsolutePath)
      .to(s"/s2a/$target")
      .build()

  override def executeInit(builderConfig: BuilderConfig) = withDocker { docker =>
    val hostConfig = HostConfig.builder
      .appendBinds(
        bindPath(builderConfig.sourceDir, "source"),
        bindPath(builderConfig.stagingDir.resolve("test_results"), "test_results"),
        bindPath(builderConfig.stagingDir.resolve("artifacts"), "artifacts")
      )
      .build()

    val containerConfig = ContainerConfig.builder
      .image(builderConfig.image)
      .cmd("tail", "-f", "/dev/null")
      .hostConfig(hostConfig)
      .build()

    val creation    = docker.createContainer(containerConfig, builderConfig.containerName)
    val containerId = creation.id

    docker.startContainer(containerId)

    Right(())
  }

  def getContainer(docker: DockerClient, name: String): Option[Container] =
    docker.listContainers(ListContainersParam.filter("name", name)).asScala.headOption

  override def executeScript(script: String, builderConfig: BuilderConfig, optional: Boolean = false): Either[NonEmptyList[ToolFormError], Unit] = withDocker { docker =>
    getContainer(docker, builderConfig.containerName)
      .toRight(NonEmptyList.of(ToolFormError("Container not initialised"))) flatMap { container =>
      val scriptPath = s"/s2a/scripts/$script.sh"
      if (!fileExists(scriptPath, container, docker)) {
        if (optional) {
          println(s"Skipping optional script [$scriptPath] missing from builder")
          Right(())
        } else {
          Left(NonEmptyList.of(ToolFormError(s"Required script [$scriptPath] not present in builder image.")))
        }
      } else {
        val result = execute(Seq(scriptPath), container, docker)

        result.exitCode match {
          case 0 => Right(())
          case _ => Left(NonEmptyList.of(ToolFormError(s"Non-zero exit code for $script phase")))
        }
      }
    }
  }

  case class ExecutionResult(exitCode: Int)

  def execute(command: Seq[String], container: Container, docker: DockerClient): ExecutionResult = {
    val exec = docker.execCreate(
      container.id,
      command.toArray
    )
    val output: LogStream = docker.execStart(exec.id)
    //output.attach(System.out, System.err, true)
    var result = docker.execInspect(exec.id)
    while (result.running) {
      Thread.sleep(500)
      result = docker.execInspect(exec.id)
    }
    //println(output.readFully)
    ExecutionResult(result.exitCode.toInt)
  }

  def fileExists(script: String, container: Container, docker: DockerClient): Boolean =
    execute(Seq("ls", script), container, docker).exitCode == 0

  override def executeCleanup(builderConfig: BuilderConfig) = withDocker { docker =>
    getContainer(docker, builderConfig.containerName) foreach { container =>
      docker.killContainer(container.id)
      docker.removeContainer(container.id)
    }

    Right(())
  }

}
