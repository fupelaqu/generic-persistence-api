package app.softnetwork.resource.api

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider, PostgresSchemaProvider}
import app.softnetwork.resource.persistence.query.{ResourceToExternalProcessorStream, ResourceToLocalFileSystemProcessorStream}

trait ResourceToLocalFileSystemApi extends ResourceApi{
  override def resourceToExternalProcessorStream: ActorSystem[_] => ResourceToExternalProcessorStream = sys =>
    new ResourceToLocalFileSystemProcessorStream() with JdbcJournalProvider with JdbcSchemaProvider {
      override implicit val system: ActorSystem[_] = sys
      override def schemaType: JdbcSchema.SchemaType = jdbcSchemaType
    }
}


