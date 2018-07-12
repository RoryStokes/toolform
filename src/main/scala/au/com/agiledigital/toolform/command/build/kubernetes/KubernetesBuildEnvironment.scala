package au.com.agiledigital.toolform.command.build.kubernetes

import java.io.File
import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.command.build.BuildEnvironment
import au.com.agiledigital.toolform.model.BuilderConfig
import cats.data.NonEmptyList
import cats.implicits._
import com.goyeau.kubernetesclient.{KubeConfig, KubernetesClient}
import com.spotify.docker.client.DockerClient.ListContainersParam
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages.Container
import com.spotify.docker.client.{DockerClient, LogStream}
import io.k8s.api.core.v1.{Namespace, NamespaceSpec}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext.Implicits.global

class KubernetesBuildEnvironment extends BuildEnvironment {
  implicit val system       = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val defaultAwait          = FiniteDuration(60, SECONDS)
  val client                = KubernetesClient(KubeConfig(new File("/home/rory/.kube/microk8s.config")))
  def bindPath(path: Path, target: String) =
    Bind
      .from(path.toFile.getAbsolutePath)
      .to(s"/s2a/$target")
      .build()

  override def executeInit(builderConfig: BuilderConfig) = {
    val project = Namespace(
      apiVersion = Some("v1"),
      metadata = Some(
        ObjectMeta(
          name = Some(builderConfig.namespace)
        ))
    )

    val fut = client.namespaces.create(project)
    val ns  = Await.result(fut, defaultAwait)

    Await.result(system.terminate(), defaultAwait)
    println(ns)
//    val hostConfig = HostConfig.builder
//      .appendBinds(
//        bindPath(builderConfig.sourceDir, "source"),
//        bindPath(builderConfig.stagingDir.resolve("test_results"), "test_results"),
//        bindPath(builderConfig.stagingDir.resolve("artifacts"), "artifacts")
//      )
//      .build()
//
//    val containerConfig = ContainerConfig.builder
//      .image(builderConfig.image)
//      .cmd("tail", "-f", "/dev/null")
//      .hostConfig(hostConfig)
//      .build()
//
//    val creation    = docker.createContainer(containerConfig, builderConfig.containerName)
//    val containerId = creation.id
//
//    docker.startContainer(containerId)

    Right(())
  }

  def getContainer(docker: DockerClient, name: String): Option[Container] =
    docker.listContainers(ListContainersParam.filter("name", name)).asScala.headOption

  override def executeScript(script: String, builderConfig: BuilderConfig, optional: Boolean = false): Either[NonEmptyList[ToolFormError], Unit] =
//    getContainer(docker, builderConfig.containerName)
//      .toRight(NonEmptyList.of(ToolFormError("Container not initialised"))) flatMap { container =>
//      val scriptPath = s"/s2a/scripts/$script.sh"
//      if (!fileExists(scriptPath, container, docker)) {
//        if (optional) {
//          println(s"Skipping optional script [$scriptPath] missing from builder")
//          Right(())
//        } else {
//          Left(NonEmptyList.of(ToolFormError(s"Required script [$scriptPath] not present in builder image.")))
//        }
//      } else {
//        val result = execute(Seq(scriptPath), container, docker)
//
//        result.exitCode match {
//          case 0 => Right(())
//          case _ => Left(NonEmptyList.of(ToolFormError(s"Non-zero exit code for $script phase")))
//        }
//      }
//    } = {
    Right(())

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

  override def executeCleanup(builderConfig: BuilderConfig) =
    Right(())

}
