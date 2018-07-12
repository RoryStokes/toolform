package au.com.agiledigital.toolform.command.build.kubernetes

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.plugin.ToolFormBuildCommandPlugin
import cats.data.NonEmptyList
import com.monovore.decline._

/**
  * Exposes [[ToolFormBuildCommandPlugin]]s under the build sub command.
  */
class BuildKubernetesCommand extends ToolFormBuildCommandPlugin {

  /**
    * The primary class for building a project with Kubernetes.
    */
  def command: Opts[Either[NonEmptyList[ToolFormError], String]] =
    Opts
      .subcommand("kubernetes", "builds project artefacts from source via Kubernetes") {
        Opts.unit
      }
      .map(execute)

  def execute(x: Unit): Either[NonEmptyList[ToolFormError], String] =
    return Right("Success")
}
