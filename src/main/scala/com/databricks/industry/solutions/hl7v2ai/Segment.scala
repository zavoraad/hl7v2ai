package com.databricks.industry.solutions.hl7v2ai

import java.util.regex.Pattern
import scala.collection.immutable.Vector

/** One segment: three-letter type (e.g. PID) and fields after each `|`. Field numbers are HL7 1-based. */
final case class Segment(segmentType: String, fields: Vector[Field], delim: Char) {
  /** HL7 1-based field index (first field after the segment type is 1). */
  def field(hl7Index: Int): Option[Field] = { 
    hl7Index - 1 match {
      case idx if idx >= 0 && idx < fields.length => Some(fields(idx))
      case _                                      => None
    }
  }

  override def toString: String =
    fields.map(_.toString).mkString(delim.toString)
}

object Segment {

  def empty(delim: Char): Segment = Segment("", Vector.empty, delim)

  def parse(
      s: String,
      delim: Char,
      componentSep: Char = '^',
      subcomponentSep: Char = '&',
  ): Segment =
    s match {
      case "" =>
        empty(delim)
      case line if line.startsWith("MSH") =>
        parseMsh(line, delim, componentSep, subcomponentSep)
      case _ =>
        val a = s.split(Pattern.quote(delim.toString), -1)
        val fields = a.drop(1).map(parseField(_, componentSep, subcomponentSep)).toVector
        Segment(a(0), fields, delim)
    }

  /** MSH-2 holds the encoding characters literally; do not split it on the component separator. */
  private def parseMsh(
      s: String,
      delim: Char,
      componentSep: Char,
      subcomponentSep: Char,
  ): Segment = {
    val a = s.split(Pattern.quote(delim.toString), -1)
    val encField = a.lift(1).getOrElse("")
    val msh2 = Field(
      Vector(Component(Vector(encField), subcomponentSep)),
      componentSep,
    )
    val rest = a.drop(2).map(parseField(_, componentSep, subcomponentSep)).toVector
    Segment(a(0), msh2 +: rest, delim)
  }

  private def parseField(raw: String, componentSep: Char, subcomponentSep: Char): Field =
    raw match {
      case "" =>
        Field(Vector.empty[Component], componentSep)
      case _ =>
        val parts = raw.split(Pattern.quote(componentSep.toString), -1)
        val components = parts.map(parseComponent(_, subcomponentSep)).toVector
        Field(components, componentSep)
    }

  private def parseComponent(raw: String, subcomponentSep: Char): Component =
    raw match {
      case "" =>
        Component(Vector.empty[String], subcomponentSep)
      case _ =>
        val parts = raw.split(Pattern.quote(subcomponentSep.toString), -1)
        Component(parts.toVector, subcomponentSep)
    }
}
