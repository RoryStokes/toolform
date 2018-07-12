package au.com.agiledigital.toolform.command.build

import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.model.{BuildPhase, BuilderConfig, Component, Project}
import au.com.agiledigital.toolform.model.BuildPhase._
import au.com.agiledigital.toolform.reader.ProjectReader
import cats.data.{NonEmptyList, ValidatedNel}
import com.monovore.decline.Opts
import cats.implicits._

trait CommonBuildCommand {
  def buildEnvironment: BuildEnvironment
  def commandName: String
  def commandHelp: String

  def command: Opts[Either[NonEmptyList[ToolFormError], String]] =
    Opts.subcommand(commandName, commandHelp) {
      (
        Opts.argument[Path]("dir"),
        Opts.option[BuildPhase]("phase", "Specify a specific phase to build").orNone
      ) mapN execute
    }

  def configForComponent(component: Component, project: Project, dir: Path) = {
    val sourcePath = dir.resolve(component.path)
    if (sourcePath.toFile.isDirectory) {
      Right(
        BuilderConfig(
          image = s"rorystokes/${component.builder}-tfbuilder",
          containerName = s"${project.id}-${component.id}-tfbuilder",
          namespace = s"${project.id}-tfbuild",
          sourceDir = sourcePath,
          stagingDir = dir.resolve(".tf").resolve(component.id)
        ))
    } else {
      Left(NonEmptyList.of(ToolFormError(s"Source directory [${sourcePath.toString}] for component [${component.id}] does not exist.")))
    }
  }

  def execute(dir: Path, phase: Option[BuildPhase]): Either[NonEmptyList[ToolFormError], String] = {
    val executor = getPhaseExecutor(phase)
    readConfig(dir) flatMap { project =>
      val results = project.components.values map { component =>
        configForComponent(component, project, dir) flatMap { config =>
          val stagingDir = config.stagingDir.toFile
          if (stagingDir.isDirectory) {
            stagingDir.delete()
          }
          stagingDir.mkdirs()
          executor(config)
        }
      }

      val maybeErrors = results.flatMap {
        case Left(errors) => errors.toList
        case _            => Nil
      }

      maybeErrors match {
        case h :: t => Left(NonEmptyList(h, t))
        case _      => Right("Success")
      }
    }
  }

  type ValidationResult[A] = ValidatedNel[ToolFormError, A]

  def getPhaseExecutor(phase: Option[BuildPhase]): BuilderConfig => Either[NonEmptyList[ToolFormError], Unit] =
    phase map {
      case Init    => buildEnvironment.executeInit(_)
      case Fetch   => buildEnvironment.executeFetch(_)
      case Prep    => buildEnvironment.executePrep(_)
      case Test    => buildEnvironment.executeTest(_)
      case Build   => buildEnvironment.executeBuild(_)
      case Stage   => buildEnvironment.executeStage(_)
      case Cleanup => buildEnvironment.executeCleanup(_)
    } getOrElse { buildConfig: BuilderConfig =>
      val res = for {
        _ <- buildEnvironment.executeInit(buildConfig)
        _ <- buildEnvironment.executeFetch(buildConfig)
        _ <- buildEnvironment.executePrep(buildConfig)
        _ <- buildEnvironment.executeTest(buildConfig)
        _ <- buildEnvironment.executeBuild(buildConfig)
        _ <- buildEnvironment.executeStage(buildConfig)
      } yield ()

      val valid: ValidationResult[Unit] = (res.toValidated, buildEnvironment.executeCleanup(buildConfig).toValidated).mapN(_ => ())

      valid.toEither
    }

  def readConfig(dir: Path) =
    ProjectReader.readProject(dir.resolve("environment.conf").toFile)

}
