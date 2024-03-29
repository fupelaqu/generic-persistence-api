package app.softnetwork.scheduler.handlers

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import app.softnetwork.concurrent.Completion
import app.softnetwork.persistence.typed.scaladsl.EntityPattern
import app.softnetwork.persistence.typed.CommandTypeKey
import app.softnetwork.scheduler.message._
import app.softnetwork.scheduler.config.{SchedulerConfig, Settings}
import org.softnetwork.akka.model.{CronTab, Schedule, Scheduler}
import app.softnetwork.scheduler.persistence.typed.SchedulerBehavior

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

/**
  * Created by smanciot on 04/09/2020.
  */
trait SchedulerTypeKey extends CommandTypeKey[SchedulerCommand]{
  override def TypeKey(implicit tTag: ClassTag[SchedulerCommand]): EntityTypeKey[SchedulerCommand] =
    SchedulerBehavior.TypeKey
}

trait SchedulerHandler extends EntityPattern[SchedulerCommand, SchedulerCommandResult] with SchedulerTypeKey

object SchedulerHandler extends SchedulerHandler

trait SchedulerDao extends Completion { _: SchedulerHandler =>

  lazy val config: SchedulerConfig = Settings.SchedulerConfig

  def start(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    resetScheduler() await{
      _ =>
        system.scheduler.scheduleOnce(
          Settings.SchedulerConfig.resetCronTabs.initialDelay.seconds,
          () => resetCronTabsAndSchedules(resetScheduler = true)
        )
    }
  }

  private[this] def resetScheduler()(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !?(ResetScheduler).map {
      case SchedulerReseted => true
      case _ => false
    }
  }

  private[scheduler] def resetCronTabsAndSchedules(resetScheduler: Boolean)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !?(ResetCronTabsAndSchedules(resetScheduler)).map {
      case CronTabsAndSchedulesReseted => true
      case _ => false
    }
  }

  private[scheduler] def addSchedule(schedule: Schedule)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (AddSchedule(schedule)).map {
      case _: ScheduleAdded => true
      case _ => false
    }
  }

  private[scheduler] def addCronTab(cronTab: CronTab)(implicit system: ActorSystem[_]): Future[Boolean] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    !? (AddCronTab(cronTab)).map {
      case _: CronTabAdded => true
      case _ => false
    }
  }

  def loadScheduler(schedulerId: Option[String] = None)(implicit system: ActorSystem[_]): Future[Option[Scheduler]] = {
    implicit val ec: ExecutionContextExecutor = system.executionContext
    schedulerId match {
      case Some(value) => 
        ??(value, LoadScheduler).map {
          case r: SchedulerLoaded => Some(r.scheduler)
          case _ => None
        }
      case None =>
        !?(LoadScheduler).map {
          case r: SchedulerLoaded => Some(r.scheduler)
          case _ => None
        }
    }
  }

}

object SchedulerDao extends SchedulerDao with SchedulerHandler
