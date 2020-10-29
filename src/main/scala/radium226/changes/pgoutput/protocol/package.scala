package radium226.changes.pgoutput

import cats.implicits._


package object protocol {

    type LogSequenceNumber = Long

    type TransactionID = Int

    type Flags = Int

    type RelationID = Int

    type RelationName = String

    type RelationNamespace = String

    object RelationID {

        def unapply(message: Message): Option[RelationID] = {
            message match {
                case Message.Commit(_, _, _) =>
                    none[RelationID]

                case Message.Begin(_, _, _) =>
                    none[RelationID]

                case Message.Insert(relationID, _) =>
                    relationID.some

                case Message.Update(relationID, _, _) =>
                    relationID.some

                case Message.Delete(relationID, _) =>
                    relationID.some

                case Message.Relation(id, _, _, _, _) =>
                    id.some
            }

        }

    }

    type TypeModifier = Int

    type DataTypeID = Int

}