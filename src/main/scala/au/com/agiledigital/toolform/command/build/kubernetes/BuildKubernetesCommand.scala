package au.com.agiledigital.toolform.command.build.kubernetes

import au.com.agiledigital.toolform.command.build.kubernetes.KubernetesBuildEnvironment
import au.com.agiledigital.toolform.command.build.{BuildEnvironment, CommonBuildCommand}
import au.com.agiledigital.toolform.plugin.ToolFormBuildCommandPlugin

/**
  * Exposes [[ToolFormBuildCommandPlugin]]s under the build sub command.
  */
class BuildKubernetesCommand extends ToolFormBuildCommandPlugin with CommonBuildCommand {

  override val commandName = "kubernetes"
  override val commandHelp = "builds project artifacts from source via Kubernetes"

  override val buildEnvironment: BuildEnvironment = new KubernetesBuildEnvironment

}
