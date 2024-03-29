package app.softnetwork.resource.api

import akka.actor.typed.ActorSystem
import app.softnetwork.persistence.jdbc.query.{JdbcJournalProvider, JdbcSchema, JdbcSchemaProvider}
import app.softnetwork.resource.model.Resource
import app.softnetwork.resource.persistence.query.{GenericResourceToExternalProcessorStream, ResourceToLocalFileSystemProcessorStream}
import app.softnetwork.resource.service.{GenericResourceService, LocalFileSystemResourceService}

trait ResourceToLocalFileSystemApi extends ResourceApi {
  override def resourceToExternalProcessorStream: ActorSystem[_] => GenericResourceToExternalProcessorStream[Resource] =
    sys =>
      new ResourceToLocalFileSystemProcessorStream() with JdbcJournalProvider with JdbcSchemaProvider {
        override implicit val system: ActorSystem[_] = sys
        override def schemaType: JdbcSchema.SchemaType = jdbcSchemaType
      }

  override def resourceService: ActorSystem[_] => GenericResourceService = sys => LocalFileSystemResourceService(sys)
}


