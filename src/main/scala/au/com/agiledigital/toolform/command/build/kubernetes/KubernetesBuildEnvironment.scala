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
import io.k8s.api.core.v1._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.k8s.api.apps.v1beta2.{Deployment, DeploymentSpec}
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}

class KubernetesBuildEnvironment extends BuildEnvironment {

  val defaultAwait = FiniteDuration(60, SECONDS)

  def withClient[A](inner: KubernetesClient => Future[Either[NonEmptyList[ToolFormError], A]]): Either[NonEmptyList[ToolFormError], A] = {
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val client                = KubernetesClient(KubeConfig(new File("/home/rory/.kube/microk8s.config")))

    val fut = for {
      result <- inner(client)
    } yield result
    val result = Try(Await.result(fut, defaultAwait)).toEither.leftMap { e =>
      e.printStackTrace()
      NonEmptyList.of(ToolFormError(s"Failed to execute Kubernetes commands: [${e.getMessage}]"))
    }.joinRight
    materializer.shutdown()
    Await.result(system.terminate(), defaultAwait)
    result
  }

  override def executeInit(builderConfig: BuilderConfig) = withClient { client =>
    val namespaceDef: Namespace = Namespace(
      metadata = Some(
        ObjectMeta(
          name = Some(builderConfig.namespace)
        ))
    )

    val deploymentDef = Deployment(
      metadata = Option(ObjectMeta(name = Option(builderConfig.containerName), namespace = Option(builderConfig.namespace))),
      spec = Option(
        DeploymentSpec(
          template = PodTemplateSpec(
            metadata = Option(
              ObjectMeta(
                labels = Option(Map("app" -> "web", "tier" -> "frontend", "environment" -> "myenv"))
              )
            ),
            spec = Option(
              PodSpec(
                containers = Seq(
                  Container(
                    name = "tfbuilder",
                    image = Some(builderConfig.image),
                    volumeMounts = Option(Seq(VolumeMount(name = "nginx-config", mountPath = "/etc/nginx/conf.d"))),
                    ports = Option(Seq(ContainerPort(name = Option("http"), containerPort = 8080)))
                  )
                ),
                volumes = Option(
                  Seq(
                    Volume(
                      name = "nginx-config",
                      configMap = Option(ConfigMapVolumeSource(name = Option("nginx-config")))
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

    println(deploymentDef)

    for {
      namespace <- client.namespaces.get(builderConfig.namespace).recoverWith {
                    case e: KubernetesException if e.statusCode == 404 =>
                      client.namespaces.create(namespaceDef).map(_ => namespaceDef)
                  }
      deployment <- client.deployments.namespace(builderConfig.namespace).create(deploymentDef)
    } yield Right(())

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
