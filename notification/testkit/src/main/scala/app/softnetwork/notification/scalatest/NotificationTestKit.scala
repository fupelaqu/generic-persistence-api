package app.softnetwork.notification.scalatest

import akka.actor.typed.ActorSystem
import app.softnetwork.notification.config.Settings.NotificationConfig
import app.softnetwork.notification.handlers.MockNotificationHandler
import app.softnetwork.notification.launch.NotificationGuardian
import app.softnetwork.notification.model.Notification
import app.softnetwork.notification.peristence.query.{NotificationCommandProcessorStream, Scheduler2NotificationProcessorStream}
import app.softnetwork.notification.peristence.typed.{MockAllNotificationsBehavior, NotificationBehavior}
import app.softnetwork.persistence.query.InMemoryJournalProvider
import app.softnetwork.scheduler.scalatest.SchedulerTestKit
import org.scalatest.Suite

trait NotificationTestKit extends SchedulerTestKit with NotificationGuardian {_: Suite =>

  /**
    *
    * @return roles associated with this node
    */
  override def roles: Seq[String] = super.roles :+ NotificationConfig.akkaNodeRole

  override def notificationBehavior: ActorSystem[_] => Option[NotificationBehavior[Notification]] = _ =>
    Some(MockAllNotificationsBehavior)

  override def scheduler2NotificationProcessorStream: ActorSystem[_] => Option[Scheduler2NotificationProcessorStream] =
    sys =>
      Some(
        new Scheduler2NotificationProcessorStream with MockNotificationHandler with InMemoryJournalProvider {
          override val tag: String = s"${MockAllNotificationsBehavior.persistenceId}-scheduler"
          override protected val forTests: Boolean = true
          override implicit def system: ActorSystem[_] = sys
        }
      )

  override def notificationCommandProcessorStream: ActorSystem[_] => Option[NotificationCommandProcessorStream] =
    sys =>
      Some(
        new NotificationCommandProcessorStream with MockNotificationHandler with InMemoryJournalProvider {
          override val forTests: Boolean = true
          override implicit def system: ActorSystem[_] = sys
        }
      )
}
