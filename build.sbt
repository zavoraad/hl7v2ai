ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.4.2"
lazy val sparkVersion = sys.env.getOrElse("SPARK_VERSION", "3.5.1")
ThisBuild / organization := "com.databricks.industry.solutions.hl7v2ai"

lazy val root = (project in file("."))
  .settings(
    name := "hl7v2",
  )
