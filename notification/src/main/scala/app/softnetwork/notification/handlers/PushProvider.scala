package app.softnetwork.notification.handlers

import java.io.{File => JFile}
import java.time.Duration
import java.util.Date
import akka.actor.typed.ActorSystem
import app.softnetwork.concurrent.Completion
import com.eatthepath.pushy.apns.{ApnsClient, ApnsClientBuilder, PushNotificationResponse}
import com.eatthepath.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPayloadBuilder, SimpleApnsPushNotification}
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.{FirebaseApp, FirebaseOptions}
import com.google.firebase.messaging._
import com.typesafe.scalalogging.StrictLogging
import app.softnetwork.config.{Settings => CommonSettings}
import app.softnetwork.notification.config.{ApnsConfig, Settings}
import org.softnetwork.notification.model._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.util.{Failure, Success, Try}

/**
  * Created by smanciot on 14/04/2018.
  */
trait PushProvider extends NotificationProvider[Push] with Completion with StrictLogging {

  val maxDevices = 1000

  override def send(notification: Push)(implicit system: ActorSystem[_]): NotificationAck = {
    // split notification per platform
    val (android, ios) = notification.devices.partition(_.platform == Platform.ANDROID)

    // send notification to devices per platform
    NotificationAck(
      None,
      apns(notification, ios.map(_.regId).distinct) ++ fcm(notification, android.map(_.regId)).distinct,
      new Date()
    )
  }

  @tailrec
  private def apns(
                    notification: Push,
                    devices: Seq[String],
                    status: Seq[NotificationStatusResult] = Seq.empty
                  )(implicit system: ActorSystem[_]): Seq[NotificationStatusResult] = {
    import APNSPushProvider._

    implicit val ec: ExecutionContextExecutor = system.executionContext

    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      val tos =
        if(nbDevices > maxDevices)
          devices.take(maxDevices)
        else
          devices

      val results =
        Future.sequence(for(to <- tos) yield {
          toScala(apnsClient.sendNotification(new SimpleApnsPushNotification(to, apnsConfig.topic, notification)))
        }) complete() match {
          case Success(responses) =>
            for(response <- responses) yield {
              val result: NotificationStatusResult = response
              result
            }
          case Failure(f) =>
            logger.error(s"send push to APNS -> ${f.getMessage}", f)
            tos.map(to => NotificationStatusResult(to, NotificationStatus.Undelivered, Some(f.getMessage)))
        }
      if(nbDevices > maxDevices){
        apns(notification, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else {
      logger.warn("send push to APNS -> no IOS device(s)")
      status
    }
  }

  @tailrec
  private def fcm(
                   notification: Push,
                   devices: Seq[String],
                   status: Seq[NotificationStatusResult] = Seq.empty
                 ): Seq[NotificationStatusResult] = {
    import FCMPushProvider._
    val nbDevices: Int = devices.length
    if(nbDevices > 0){
      implicit val tokens: Seq[String] =
        if(nbDevices > maxDevices)
          devices.take(maxDevices)
        else
          devices
      val results: Seq[NotificationStatusResult] =
        Try(
          FirebaseMessaging.getInstance(firebaseApp).sendMulticast(notification)
        ) match {
          case Success(s) =>
            logger.info(s"send push to FCM -> $s")
            s
          case Failure(f) =>
            logger.error(s"send push to FCM -> ${f.getMessage}", f)
            tokens.map(token => NotificationStatusResult(token, NotificationStatus.Undelivered, Some(f.getMessage)))
        }
      if(nbDevices > maxDevices){
        fcm(notification, devices.drop(maxDevices), status ++ results)
      }
      else{
        status ++ results
      }
    }
    else{
      logger.warn("send push to FCM -> no ANDROID device(s)")
      status
    }
  }

}

object APNSPushProvider {

  lazy val apnsConfig: ApnsConfig = Settings.NotificationConfig.push.apns

  lazy val apnsClient: ApnsClient =
    clientCredentials(
      new ApnsClientBuilder()
        .setApnsServer(
          if (apnsConfig.dryRun) {
            ApnsClientBuilder.DEVELOPMENT_APNS_HOST
          } else {
            ApnsClientBuilder.PRODUCTION_APNS_HOST
          }
        )
    ).setConnectionTimeout(Duration.ofSeconds(CommonSettings.DefaultTimeout.toSeconds)).build()

  implicit def toApnsPayload(notification: Push): String = {
    val apnsPayload = new SimpleApnsPayloadBuilder()
      .setAlertTitle(notification.subject)
      .setAlertBody(notification.message)
      .setSound(notification.sound.getOrElse(ApnsPayloadBuilder.DEFAULT_SOUND_FILENAME))
    if(notification.badge > 0) {
      apnsPayload.setBadgeNumber(notification.badge)
    }
    apnsPayload.build()
  }

  implicit def toNotificationStatusResult(result: PushNotificationResponse[SimpleApnsPushNotification]): NotificationStatusResult = {
    val error = Option(result.getRejectionReason) match {
      case Some(e) => Some(s"${result.getPushNotification.getToken} -> $e")
      case _ => None
    }
    NotificationStatusResult(
      result.getPushNotification.getToken,
      if (result.isAccepted)
        NotificationStatus.Sent
      else
        NotificationStatus.Rejected,
      error
    )
  }

  def clientCredentials: ApnsClientBuilder => ApnsClientBuilder = builder => {
    val file = new JFile(apnsConfig.keystore.path)
    if(file.exists){
      builder.setClientCredentials(file, apnsConfig.keystore.password)
    }
    else{
      builder.setClientCredentials(
        getClass.getClassLoader.getResourceAsStream(apnsConfig.keystore.path),
        apnsConfig.keystore.password
      )
    }
  }

}

object FCMPushProvider{

  lazy val firebaseApp: FirebaseApp = {
    val databaseUrl = Settings.NotificationConfig.push.fcm.databaseUrl
    val options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault())
    if(databaseUrl.nonEmpty){
      options.setDatabaseUrl(databaseUrl)
    }
    FirebaseApp.initializeApp(options.build())
  }

  implicit def toFcmPayload(notification: Push)(implicit tokens: Seq[String]): MulticastMessage = {
    val androidNotification =  AndroidNotification.builder()
      .setTitle(notification.subject)
      .setBody(notification.message)
      .setSound(notification.sound.getOrElse("default"))
    if(notification.badge > 0){
      androidNotification.setNotificationCount(notification.badge)
    }
    val payload = MulticastMessage.builder()
      .setAndroidConfig(
        AndroidConfig.builder().setNotification(androidNotification.build()).build()
      )
      .addAllTokens(tokens.asJava)
      .build()
    payload
  }

  implicit def toNotificationResults(response: BatchResponse)(implicit tokens: Seq[String]): Seq[NotificationStatusResult] = {
    for((r, i) <- response.getResponses.asScala.zipWithIndex) yield
      NotificationStatusResult(
        tokens(i),
        if (r.isSuccessful)
          NotificationStatus.Sent
        else
          NotificationStatus.Rejected,
        Option(r.getException).map(e => e.getMessage)
      )
  }
}

trait MockPushProvider extends PushProvider with MockNotificationProvider[Push]

object PushProvider extends PushProvider

object MockPushProvider extends MockPushProvider
