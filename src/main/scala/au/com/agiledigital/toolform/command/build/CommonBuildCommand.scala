package au.com.agiledigital.toolform.command.build

import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.reader.ProjectReader
import cats.data.NonEmptyList

trait CommonBuildCommand {
  def buildEnvironment: BuildEnvironment

  def readConfig(dir: Path) =
    ProjectReader.readProject(dir.resolve("environment.conf").toFile)

  def build(dir: Path) =
    println(cleanup(dir))

  def initialise(dir: Path): Either[NonEmptyList[ToolFormError], String] = readConfig(dir) map { project =>
    project.components.values foreach { component =>
      buildEnvironment.initialiseBuilder(component, project)
    }
    "Initialised containers"
  }

  def cleanup(dir: Path): Either[NonEmptyList[ToolFormError], String] = readConfig(dir) map { project =>
    project.components.values foreach { component =>
      buildEnvironment.cleanupBuilder(component, project)
    }
    "Cleaned up containers"
  }
}
