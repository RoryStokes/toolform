package au.com.agiledigital.toolform.model

import java.nio.file.Path

case class BuilderConfig(
    image: String,
    containerName: String,
    namespace: String,
    sourceDir: Path,
    stagingDir: Path
)
