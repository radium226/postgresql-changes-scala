package radium226.changes.pgoutput.protocol

case class Column(flags: Flags, name: String, dataTypeID: DataTypeID, typeModifier: TypeModifier)
