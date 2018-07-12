package au.com.agiledigital.toolform.util

import cats.data.ValidatedNel
import enumeratum.{Enum, EnumEntry}
import cats.syntax.option._
import com.monovore.decline.Argument

trait EnumArgument[A <: EnumEntry] { self: Enum[A] =>
  implicit val argument: Argument[A] = new Argument[A] {
    val defaultMetavar: String                      = "enum"
    def read(name: String): ValidatedNel[String, A] = self.withNameInsensitiveOption(name).toValidNel(s"Invalid value: $name")
  }
}
