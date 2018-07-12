package au.com.agiledigital.toolform.model

import au.com.agiledigital.toolform.util.EnumArgument
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable.IndexedSeq

sealed trait BuildPhase extends EnumEntry
sealed trait ScriptExecutionPhase

object BuildPhase extends Enum[BuildPhase] with EnumArgument[BuildPhase] {
  val values: IndexedSeq[BuildPhase] = findValues

  case object Init    extends BuildPhase
  case object Fetch   extends BuildPhase with ScriptExecutionPhase
  case object Prep    extends BuildPhase with ScriptExecutionPhase
  case object Test    extends BuildPhase with ScriptExecutionPhase
  case object Build   extends BuildPhase with ScriptExecutionPhase
  case object Stage   extends BuildPhase with ScriptExecutionPhase
  case object Cleanup extends BuildPhase

}
