package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.Vector

final case class Field(components: Vector[Component], delim: Char) {
  override def toString: String =
    components.map(_.toString).mkString(delim.toString)
}
