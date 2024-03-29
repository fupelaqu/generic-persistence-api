package app.softnetwork.scheduler

import app.softnetwork.persistence.now

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import com.markatta.akron.CronExpression
import com.typesafe.scalalogging.StrictLogging
import org.softnetwork.akka.model.Schedule

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 11/05/2021.
  */
package object model {

  trait SchedulerItem {
    def persistenceId: String
    def entityId: String
    def key: String
    val uuid = s"$persistenceId#$entityId#$key"
  }

  trait SchedulerDecorator { _: Schedule =>
    val triggerable: Boolean =
//      // the schedule can be triggered repeatedly
//      repeatedly.getOrElse(false) ||
      // the schedule has never been triggered and has no scheduled date
      (lastTriggered.isEmpty && scheduledDate.isEmpty) ||
      // the schedule should be triggered at a specified date that has been reached
      (scheduledDate.isDefined &&
        now().after(getScheduledDate) &&
        (lastTriggered.isEmpty || getLastTriggered.before(getScheduledDate)))
  }

  trait CronTabItem extends StrictLogging {
    def cron: String
    lazy val cronExpression: CronExpression = Try{CronExpression(cron)} match {
      case Success(s) => s
      case Failure(f) =>
        logger.error(f.getMessage + s" -> [$cron]")
        CronExpression("*/5 * * * *") // By default every 5 minutes
    }
    def nextLocalDateTime(): Option[LocalDateTime] = {
      cronExpression.nextTriggerTime(LocalDateTime.now())
    }
    def next(from: Option[Date] = None): Option[FiniteDuration] = {
      (from match {
        case Some(s) => Some(new Timestamp(s.getTime).toLocalDateTime)
        case _ => nextLocalDateTime()
      }) match {
        case Some(ldt) =>
          val diff = LocalDateTime.now().until(ldt, ChronoUnit.SECONDS)
          if(diff < 0){
            Some(Math.max(1, 60 - Math.abs(diff)).seconds)
          }
          else{
            Some(diff.seconds)
          }

        case _ => None
      }
    }
  }
}
