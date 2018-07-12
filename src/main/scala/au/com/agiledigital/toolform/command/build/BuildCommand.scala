package au.com.agiledigital.toolform.command.build

import java.util.ServiceLoader

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.plugin.{ToolFormBuildCommandPlugin, ToolFormCommandPlugin}
import cats.data.NonEmptyList
import com.monovore.decline._

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq

/**
  * Exposes [[ToolFormBuildCommandPlugin]]s under the build sub command.
  */
class BuildCommand extends ToolFormCommandPlugin {

  override val command: Opts[Either[NonEmptyList[ToolFormError], String]] = {
    Opts
      .subcommand("build", "Build project artifacts via a particular mechanism") {
        BuildCommand.plugins.map(_.command).reduce(_ orElse _)
      }
  }
}

/**
  * Discovers and loads all available [[ToolFormBuildCommandPlugin]]s.
  */
object BuildCommand {

  /**
    * Available command plugins loaded from the runtime environment.
    *
    * @return Collection of command plugins.
    */
  def plugins: Seq[ToolFormBuildCommandPlugin] = {
    val serviceLoaderIterator                               = ServiceLoader.load(classOf[ToolFormBuildCommandPlugin]).iterator()
    val scalaIterator: Iterator[ToolFormBuildCommandPlugin] = serviceLoaderIterator.asScala
    scalaIterator.toIndexedSeq
  }
}
