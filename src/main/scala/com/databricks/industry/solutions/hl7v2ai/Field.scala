package com.databricks.industry.solutions.hl7v2ai

final case class Field(components: ArraySeq[Component], delim: Char){
    override def toString: String =
      components.map(_.toString).mkString(delim.toString)
}

