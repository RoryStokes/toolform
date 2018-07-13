package au.com.agiledigital.toolform.command.build.kubernetes

import java.io._
import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.command.build.BuildEnvironment
import au.com.agiledigital.toolform.model.BuilderConfig
import cats.data.NonEmptyList
import cats.implicits._
import com.spotify.docker.client.{DockerClient, LogStream}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS

import com.google.common.io.CharStreams
import io.fabric8.kubernetes.api.model.{DoneablePod, Namespace, Pod}
import io.fabric8.kubernetes.client.dsl.{ContainerResource, ExecWatch, LogWatch, PodResource}
import io.fabric8.kubernetes.client.internal.KubeConfigUtils
import io.fabric8.kubernetes.client.utils.InputStreamPumper
import io.fabric8.kubernetes.client.{ConfigBuilder, DefaultKubernetesClient, KubernetesClient}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream, TarArchiveOutputStream}
import org.apache.commons.io.IOUtils

class KubernetesBuildEnvironment extends BuildEnvironment {

  val defaultAwait = FiniteDuration(60, SECONDS)
  val delay        = FiniteDuration(10, SECONDS)
  val client       = new DefaultKubernetesClient()

  def waitUntilStatus(builderConfig: BuilderConfig, status: Option[String], retries: Int): Either[NonEmptyList[ToolFormError], Pod] = {
    val pod = client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)
      .get()

    if (Option(pod).map(_.getStatus.getPhase) == status) {
      Right(pod)
    } else if (retries > 0) {
      Thread.sleep(1000)
      waitUntilStatus(builderConfig, status, retries - 1)
    } else {
      Left(NonEmptyList.of(ToolFormError(s"Timed out waiting for pod [${builderConfig.containerName}] to enter desired state [$status]")))
    }
  }

  override def executeInit(builderConfig: BuilderConfig) = {
    Option(client.namespaces.withName(builderConfig.namespace).get()).getOrElse(
      client.namespaces
        .createNew()
        .withNewMetadata
        .withName(builderConfig.namespace)
        .endMetadata()
        .done())

    Option(client.pods.inNamespace(builderConfig.namespace).withName(builderConfig.containerName).get()).getOrElse(
      client.pods
        .inNamespace(builderConfig.namespace)
        .createOrReplaceWithNew()
        .withNewMetadata()
        .withName(builderConfig.containerName)
        .endMetadata()
        .withNewSpec()
        .addNewContainer()
        .withName("tfbuilder")
        .withImage(builderConfig.image)
        .withCommand("tail", "-f", "/dev/null")
        .endContainer()
        .endSpec()
        .done())

    waitUntilStatus(builderConfig, Some("Running"), 30)

    client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)
      .inContainer("tfbuilder")
      .redirectingOutput()
      .exec("mkdir", "-p", s"/s2a/test_results", s"/s2a/artifacts")
      .close()

    val tempFile    = File.createTempFile(s"${builderConfig.containerName}-source-", ".tar")
    var writeStream = new FileOutputStream(tempFile)
    val tarOut      = new TarArchiveOutputStream(writeStream)
    streamTar(builderConfig.sourceDir.toFile, tarOut, "source")
    val readStream = new FileInputStream(tempFile)

    val watch = client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)
      .inContainer("tfbuilder")
      .readingInput(readStream)
      .exec("tar", "xf", "-", "-C", "/s2a")

    watch.close()
    readStream.close()

    tempFile.delete()

    Right(())
  }

  def streamTar(f: File, tarOut: TarArchiveOutputStream, base: String): Unit = {
    val entry = new TarArchiveEntry(f, base)
    tarOut.putArchiveEntry(entry)
    if (f.isFile) {
      println(f.getAbsolutePath)
      val fStream = new FileInputStream(f)
      IOUtils.copy(fStream, tarOut)
      fStream.close()
      tarOut.closeArchiveEntry()
    } else {
      tarOut.closeArchiveEntry()
      f.listFiles.foreach(file => streamTar(file, tarOut, s"$base/${file.getName}"))
    }
  }

  def downloadDir(dir: String, builderConfig: BuilderConfig, pod: PodResource[Pod, DoneablePod]): Unit = {
    val file        = builderConfig.stagingDir.resolve(s"$dir.tar").toFile
    val writeStream = new FileOutputStream(file)
    val watch = pod
      .inContainer("tfbuilder")
      .writingOutput(writeStream)
      .exec("tar", "cf", "-", "--dir", s"/s2a/$dir", ".")
    watch.close()
  }

  override def executeScript(script: String, builderConfig: BuilderConfig, optional: Boolean = false): Either[NonEmptyList[ToolFormError], Unit] = {

    client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)
      .inContainer("tfbuilder")
      .redirectingOutput()
      .exec(s"/s2a/scripts/$script.sh")

    Right(())

  }
  case class ExecutionResult(exitCode: Int)

//  def execute(command: Seq[String], container: Container, docker: DockerClient): ExecutionResult =
//    ExecutionResult(0)
//
//  def fileExists(script: String, container: Container, docker: DockerClient): Boolean =
//    execute(Seq("ls", script), container, docker).exitCode == 0

  override def executeCleanup(builderConfig: BuilderConfig) = {
    val pod = client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)

    downloadDir("test_results", builderConfig, pod)
    downloadDir("artifacts", builderConfig, pod)
    println(s"Deleting ${builderConfig.containerName}")
    client.pods
      .inNamespace(builderConfig.namespace)
      .withName(builderConfig.containerName)
      .delete()
    Right(())
  }

}
