package au.com.agiledigital.toolform.command.build.docker

import au.com.agiledigital.toolform.command.build.{BuildEnvironment, CommonBuildCommand}
import au.com.agiledigital.toolform.plugin.ToolFormBuildCommandPlugin

/**
  * Exposes [[ToolFormBuildCommandPlugin]]s under the build sub command.
  */
class BuildDockerCommand extends ToolFormBuildCommandPlugin with CommonBuildCommand {

  override val commandName = "docker"
  override val commandHelp = "builds project artifacts from source via Docker"

  override val buildEnvironment: BuildEnvironment = new DockerBuildEnvironment

}
