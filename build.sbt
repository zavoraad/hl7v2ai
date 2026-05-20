ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"
lazy val sparkVersion = sys.env.getOrElse("SPARK_VERSION", "3.5.1")
ThisBuild / organization := "com.databricks.industry.solutions.hl7v2ai"

lazy val root = (project in file("."))
  .settings(
    name := "hl7v2ai",
    libraryDependencies += {
      val scalaTestV = "3.2.14"
      "org.scalatest" %% "scalatest" % scalaTestV % "test",
    }
  )
