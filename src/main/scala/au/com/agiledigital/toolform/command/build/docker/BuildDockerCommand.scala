package au.com.agiledigital.toolform.command.build.docker

import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.command.build.{BuildEnvironment, CommonBuildCommand}
import au.com.agiledigital.toolform.plugin.ToolFormBuildCommandPlugin
import cats.data.NonEmptyList
import com.monovore.decline._

/**
  * Exposes [[ToolFormBuildCommandPlugin]]s under the build sub command.
  */
class BuildDockerCommand extends ToolFormBuildCommandPlugin with CommonBuildCommand {

  /**
    * The primary class for building a project with Kubernetes.
    */
  def command: Opts[Either[NonEmptyList[ToolFormError], String]] =
    Opts
      .subcommand("docker", "builds project artefacts from source via Docker") {
        Opts.argument[Path]("dir")
      }
      .map(build)

  override def buildEnvironment: BuildEnvironment = new DockerBuildEnvironment

}
