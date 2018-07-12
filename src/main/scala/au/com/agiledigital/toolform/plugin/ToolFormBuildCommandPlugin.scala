package au.com.agiledigital.toolform.plugin

import au.com.agiledigital.toolform.app.ToolFormError
import cats.data.NonEmptyList
import com.monovore.decline._

/**
  * Extension point to add new build commands to toolform.
  * Uses Java SPI - See [[java.util.ServiceLoader]] for details.
  * Implement the trait and register the new implementation in
  * META-INF/services/au.com.agiledigital.toolform.plugin.ToolFormBuildCommandPlugin
  * on the runtime classpath.
  */
trait ToolFormBuildCommandPlugin {
  def command: Opts[Either[NonEmptyList[ToolFormError], String]]
}
