package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.Vector

final case class Component(subcomponents: Vector[String], delim: Char) {
  /** HL7 1-based subcomponent index within this component. */
  def subcomponent(hl7Index: Int): Option[String] = {
    val i = hl7Index - 1
    i match {
      case idx if idx >= 0 && idx < subcomponents.length => Some(subcomponents(idx))
      case _                                             => None
    }
  }

  override def toString: String =
    subcomponents.mkString(delim.toString)
}
