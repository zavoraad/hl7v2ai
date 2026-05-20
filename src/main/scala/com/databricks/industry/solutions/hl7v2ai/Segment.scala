package com.databricks.industry.solutions.hl7v2ai

import java.util.regex.Pattern
import scala.collection.immutable.ArraySeq

/** One segment: three-letter type (e.g. PID) and fields after each `|`. Field numbers are HL7 1-based. */
final case class Segment(segmentType: String, fields: ArraySeq[String], delim: Char)

object Segment {

  def empty(delim: Char): Segment = Segment("", ArraySeq.empty, delim)

  def parse(s: String, delim: Char): Segment = {
    if s.isEmpty then empty(delim)
    else
      val a = s.split(Pattern.quote(delim.toString), -1)
      val fields = ArraySeq.unsafeWrapArray(a)
      Segment(a(0), fields, delim)
  }
}
