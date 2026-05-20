# hl7v2ai API reference (for LLM code generation)

Use this document as the **ground truth** when generating Scala or Spark code. Do not reimplement HL7 parsing by hand (no ad-hoc `split("|")` for production paths). Use the types and methods below.

**Package:** `com.databricks.industry.solutions.hl7v2ai`  
**Scala:** 2.12.8  
**Spark (target):** 3.5.x — parsing library is separate from Spark; wire Spark via `map` / UDF as shown below.

---

## Instructions for the model

1. Prefer `HL7v2AI.fromRawString(raw)` when input is a full ER7 message string.
2. Use **HL7 1-based** indexes for `Segment.field(n)` and `Component.subcomponent(n)` (e.g. PID-3 → `field(3)`).
3. Use `msg.segment("PID")` for body segments only; it returns `Vector[Segment]` (0..n). Use `msg.messageHeader` for MSH.
4. Handle `Option` / empty `Vector` — missing segments and fields are normal.
5. Do not assume default delimiters if parsing raw messages; delimiters come from MSH when using `fromRawString`.
6. Load **bundled config JSON** via `ConfigResources` (HL7 version, EHR, implementation guide) for prompts; this library does not validate IG rules at runtime.

---

## Type hierarchy

```
HL7v2AI
├── messageHeader: Segment          // MSH only
├── segments: Vector[Segment]       // body (PID, OBX, …)
├── delim: Char                     // MSH-1 field separator
└── hl7Version: String              // MSH-12 (e.g. "2.5.1") or ""

Segment
├── segmentType: String             // e.g. "PID"
├── fields: Vector[Field]           // HL7 fields after segment name
└── delim: Char                     // field separator for this segment

Field
├── components: Vector[Component]
└── delim: Char                     // component separator (from MSH-2)

Component
├── subcomponents: Vector[String]
└── delim: Char                     // subcomponent separator (from MSH-2)
```

---

## HL7v2AI

### Case class

```scala
final case class HL7v2AI(
  messageHeader: Segment,
  segments: Vector[Segment],
  delim: Char,
  hl7Version: String,
)
```

| Member | Description |
|--------|-------------|
| `messageHeader` | Parsed `MSH` segment |
| `segments` | All non-MSH segments in file order |
| `delim` | Field separator (MSH-1), e.g. `\|` |
| `hl7Version` | Version ID from MSH-12, or `""` |

### Instance methods

```scala
def segment(name: String): Vector[Segment]
```

- Filters **body** `segments` by `segmentType` (case-insensitive).
- Does **not** search `messageHeader`. `segment("MSH")` is always empty; use `messageHeader`.

```scala
override def toString: String
```

- Joins **body** segment field strings with `delim`.
- Does **not** include `MSH`.

### Companion object — parsing

```scala
def fromRawString(raw: String): Option[HL7v2AI]
```

**Primary entry point.** One ER7 message (multiple segment lines). Line breaks: `\n`, `\r`, or `\r\n`. Returns `None` if the first line is not a valid `MSH` with a field separator.

```scala
def rawToOrderedSegments(raw: String): Option[Vector[Segment]]
```

- Parses every line to `Segment`, using MSH-1 and MSH-2 from the first line for all segments.

```scala
def fromOrderedSegments(allSegments: Vector[Segment]): Option[HL7v2AI]
```

- `head` = MSH, `tail` = body. `None` if empty.

```scala
def fromParts(messageHeader: Segment, body: Seq[Segment]): HL7v2AI
```

- Build when segments are already parsed.

### Companion object — delimiters

```scala
final case class MshDelimiters(
  field: Char,        // MSH-1
  component: Char,    // MSH-2 position 1
  repetition: Char,  // MSH-2 position 2 (not used in parse yet)
  escape: Char,       // MSH-2 position 3 (not used in parse yet)
  subcomponent: Char, // MSH-2 position 4
)

def delimitersFromMshLine(mshLine: String): Option[MshDelimiters]
```

Example MSH line: `MSH|^~\&|SENDING` → field `|`, component `^`, subcomponent `&`.

---

## Segment

```scala
final case class Segment(
  segmentType: String,
  fields: Vector[Field],
  delim: Char,
)

def field(hl7Index: Int): Option[Field]   // HL7 1-based; first field after segment name = 1
```

### Companion

```scala
def empty(delim: Char): Segment

def parse(
  s: String,
  delim: Char,
  componentSep: Char = '^',
  subcomponentSep: Char = '&',
): Segment
```

- Non-MSH: splits fields on `delim`, then components / subcomponents.
- **MSH:** MSH-2 encoding field is kept as one literal field (not split on `componentSep`).

---

## Field

```scala
final case class Field(components: Vector[Component], delim: Char)

override def toString: String   // components joined with delim (e.g. ^)
```

No `field()` helper — navigate via `Segment.field(hl7Index)`.

---

## Component

```scala
final case class Component(subcomponents: Vector[String], delim: Char)

def subcomponent(hl7Index: Int): Option[String]   // HL7 1-based

override def toString: String   // subcomponents joined with delim (e.g. &)
```

---

## Common access patterns

### Full message from raw string

```scala
import com.databricks.industry.solutions.hl7v2ai.HL7v2AI

val raw: String = ???  // one HL7 message
HL7v2AI.fromRawString(raw) match {
  case None => // invalid or missing MSH
  case Some(msg) =>
    val version = msg.hl7Version
    val msh = msg.messageHeader
    val pids = msg.segment("PID")
    val firstPid3 = pids.headOption
      .flatMap(_.field(3))
      .map(_.toString)
}
```

### PID-3 patient identifier (first component / subcomponent)

HL7 PID-3 is often `ID^IDType^...`:

```scala
val id = for {
  msg <- HL7v2AI.fromRawString(raw)
  pid <- msg.segment("PID").headOption
  f   <- pid.field(3)
  c   <- f.components.headOption
  v   <- c.subcomponent(1)
} yield v
```

### All OBX segments

```scala
val obxSegments = msg.segment("OBX")   // Vector — may be empty, one, or many
obxSegments.flatMap(_.field(5).map(_.toString))
```

### MSH-9 message type (example)

After parse, use `messageHeader` and **HL7 field numbers** (MSH has special indexing in the spec; in this model MSH-2 is `field(1)` as the encoding characters field):

```scala
msg.messageHeader.field(9).map(_.toString)   // verify against your IG
```

---

## Spark integration pattern

Assume one column `hl7_raw: String` per row (full message). Parse inside `map` or `flatMap`; avoid collecting large datasets to the driver.

```scala
import com.databricks.industry.solutions.hl7v2ai.HL7v2AI
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession

// Example: extract PID-3 from first PID, or null
def firstPid3(raw: String): Option[String] =
  for {
    msg <- HL7v2AI.fromRawString(raw)
    pid <- msg.segment("PID").headOption
    f   <- pid.field(3)
  } yield f.toString

// Dataset[String] or DataFrame with "body" column
val parsed = df.map(row => firstPid3(row.getAs[String]("hl7_raw")))

// Or UDF registered for SQL
spark.udf.register("hl7_first_pid3", (raw: String) => firstPid3(raw).orNull)
```

```scala
// Multiple OBX per message → explode in Spark after building a Seq in map
df.flatMap { row =>
  HL7v2AI.fromRawString(row.getAs[String]("hl7_raw")) match {
    case None => Seq.empty
    case Some(msg) =>
      msg.segment("OBX").zipWithIndex.map { case (seg, i) =>
        (i, seg.field(5).map(_.toString).getOrElse(""))
      }
  }
}
```

**Rules for Spark codegen:**

- Ship `hl7v2ai` JAR on the cluster classpath.
- Treat parse failures as `None` / null / empty list; do not throw on bad rows unless the job requires it.
- Use `mapPartitions` if you batch-parse many messages per partition for efficiency.
- Output schema should be explicit (`StructType` or case class), not `Any`.

---

## Delimiters (MSH)

| Level | Source | Stored on |
|-------|--------|-----------|
| Field | MSH-1 | `Segment.delim`, `HL7v2AI.delim` |
| Component | MSH-2 char 1 | `Field.delim` |
| Subcomponent | MSH-2 char 4 | `Component.delim` |

When using `fromRawString`, all segment lines use delimiters from the first `MSH` line. When building tests manually, pass the same separators to `Segment.parse`.

---

## Limitations (do not generate code that assumes these exist)

- No HL7 **escape** processing (`\F\`, `\S\`, etc.).
- No **repetition** separator (`~`) handling for repeated fields.
- No conformance validation against an implementation guide.
- `hl7Version` from parsed MSH internal layout (MSH-12 at field index 10 after parse).
- `toString` on `HL7v2AI` omits MSH; not a full wire-format round trip of the entire message.

---

## Anti-patterns (do not generate)

```scala
// BAD — bypasses MSH delimiters and MSH-2 rules
raw.split("\\|").drop(1)

// BAD — only first PID when IG allows many
msg.segment("PID").head  // use .headOption and document assumption

// BAD — wrong index (0-based HL7)
pid.field(2)  // when user asked for PID-3

// BAD — MSH via segment()
msg.segment("MSH")  // always empty
```

---

## Bundled config (`ConfigResources`)

Configs live in the JAR at `/hl7v2ai/config/{hl7-version|ehr|implementation-guides}/<id>.json`.

```scala
import com.databricks.industry.solutions.hl7v2ai.ConfigResources

ConfigResources.listHl7Versions()
ConfigResources.listEhrProfiles()
ConfigResources.listImplementationGuides()

val versionJson = ConfigResources.loadHl7Version("2.5.1")           // Option[String]
val ehrJson     = ConfigResources.loadEhr("example-hospital")
val igJson      = ConfigResources.loadImplementationGuide("example-hospital-adt-a01")

// Attach to LLM prompt (parse JSON in application code if needed)
val promptContext = for {
  v <- ConfigResources.loadHl7Version("2.5.1")
  e <- ConfigResources.loadEhr("example-hospital")
  g <- ConfigResources.loadImplementationGuide("example-hospital-adt-a01")
} yield s"HL7 version:\n$v\n\nEHR:\n$e\n\nIG:\n$g"
```

```scala
ConfigResources.catalog()  // Vector[ResourceRef] — all bundled ids + classpath paths
```

---

## Minimal imports

```scala
import com.databricks.industry.solutions.hl7v2ai.{
  HL7v2AI, Segment, Field, Component, ConfigResources,
}
```

---

## Related project docs

- Human-oriented overview: [README.md](../README.md)
- Roadmap (config files, prompt builder, Spark module): README **Next steps**
