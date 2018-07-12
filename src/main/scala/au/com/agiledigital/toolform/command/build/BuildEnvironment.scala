package au.com.agiledigital.toolform.command.build

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.model.{BuilderConfig, Component, Project}
import cats.data.NonEmptyList

trait BuildEnvironment {
  def executeInit(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit]
  def executeScript(script: String, buildConfig: BuilderConfig, optional: Boolean = false): Either[NonEmptyList[ToolFormError], Unit]
  def executeCleanup(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit]

  def executeFetch(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit] =
    executeScript("fetch", buildConfig, optional = true)
  def executePrep(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit] =
    executeScript("prep", buildConfig, optional = true)
  def executeTest(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit] = {
    println(buildConfig)
    println("testing")
    executeScript("test", buildConfig)
  }
  def executeBuild(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit] =
    executeScript("build", buildConfig)
  def executeStage(buildConfig: BuilderConfig): Either[NonEmptyList[ToolFormError], Unit] =
    executeScript("stage", buildConfig, optional = true)
}
