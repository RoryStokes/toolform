package au.com.agiledigital.toolform.command.build.kubernetes

import java.io.File
import java.nio.file.Path

import au.com.agiledigital.toolform.app.ToolFormError
import au.com.agiledigital.toolform.command.build.BuildEnvironment
import au.com.agiledigital.toolform.model.BuilderConfig
import cats.data.NonEmptyList
import cats.implicits._
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient, KubernetesException}
import com.spotify.docker.client.{DockerClient, LogStream}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.{ActorSystem, Scheduler}
import akka.http.javadsl.model.ws.Message
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.pattern.after
import akka.stream.scaladsl.Flow
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import skuber.Container.Running
import skuber.Pod.Phase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}
import skuber._
import skuber.json.format._
import skuber.apps.v1.Deployment

class KubernetesBuildEnvironment extends BuildEnvironment {

  val defaultAwait = FiniteDuration(60, SECONDS)
  val delay        = FiniteDuration(10, SECONDS)

  implicit var system = ActorSystem()

  def withClient[A](inner: (KubernetesClient, K8SRequestContext) => Future[Either[NonEmptyList[ToolFormError], A]]): Either[NonEmptyList[ToolFormError], A] = {
    val configFile = new File("/home/rory/.kube/microk8s.config")
    system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val client                = KubernetesClient(KubeConfig(configFile))

    K8SConfiguration.parseKubeconfigFile(configFile.toPath).toEither.leftMap { e =>
      e.printStackTrace()
      NonEmptyList.of(ToolFormError(s"Failed to parse kubernetes config Kubernetes commands: [${configFile.getAbsolutePath}]"))
    } flatMap { config =>
      val k8s = k8sInit(config)

      val fut = for {
        result <- inner(client, k8s)
      } yield result
      val result = Try(Await.result(fut, defaultAwait)).toEither.leftMap { e =>
        e.printStackTrace()
        NonEmptyList.of(ToolFormError(s"Failed to execute Kubernetes commands: [${e.getMessage}]"))
      }.joinRight
      materializer.shutdown()
      Await.result(system.terminate(), defaultAwait)
      result
    }

  }

  def retryUntilSome[T](op: => Future[Option[T]], delay: FiniteDuration, retries: Int): Future[Either[NonEmptyList[ToolFormError], T]] =
    op flatMap {
      case None if retries > 0 => after(delay, system.scheduler)(retryUntilSome(op, delay, retries - 1))
      case Some(value)         => Future.successful(Right(value))
      case _                   => Future.successful(Left(NonEmptyList.of(ToolFormError(s"Operation timed out after multiple retries"))))
    }

  def getPod(selector: LabelSelector, client: KubernetesClient, k8s: K8SRequestContext): Future[Option[Pod]] =
    (k8s listSelected [PodList] selector) map { pods: PodList =>
      pods.toList.find(_.status.flatMap(_.phase).contains(Phase.Running))
    }

  def copyToPod(builderConfig: BuilderConfig, pod: Pod, source: Path, destination: String, client: KubernetesClient, k8s: K8SRequestContext
               ): Future[Either[NonEmptyList[ToolFormError], Unit]] = {
    val flow = Flow.fromFunction { x: Either[Status, String] =>
      TextMessage("No idea what this is for")
    }.mapMaterializedValue(_ => Future.successful("Done"))
    client.pods.namespace(k8s.namespaceName).exec[String](
      pod.name,
      flow,
      container = Some("tfbuilder"),
      command = Seq.empty,
      stdin = false,
      stdout = true,
      stderr = true,
      tty = false
    )

    Future.successful(Right(()))
  }

  override def executeInit(builderConfig: BuilderConfig) = withClient {
    case (client, k8s) =>
      val containerDef = Container(
        name = "tfbuilder",
        image = builderConfig.image,
        command = List("tail", "-f", "/dev/null")
      )

      val selector = LabelSelector(LabelSelector.IsEqualRequirement("component", builderConfig.containerName))

      val template = Pod.Template.Spec.named("tfbuilder").addContainer(containerDef).addLabel("component" -> builderConfig.containerName)

      val deploymentDef = Deployment(builderConfig.containerName)
        .withReplicas(1)
        .withTemplate(template)
        .withLabelSelector(selector)

      for {
        dep <- (k8s getOption [Deployment] builderConfig.containerName) flatMap {
                case Some(_) => k8s update deploymentDef
                case None    => k8s create deploymentDef
              }
        pod <- retryUntilSome(getPod(selector, client, k8s), delay, 12)
      } yield pod.map(_ => ())
  }

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

  def execute(command: Seq[String], container: Container, docker: DockerClient): ExecutionResult =
    ExecutionResult(0)

  def fileExists(script: String, container: Container, docker: DockerClient): Boolean =
    execute(Seq("ls", script), container, docker).exitCode == 0

  override def executeCleanup(builderConfig: BuilderConfig) =
    Right(())

}
