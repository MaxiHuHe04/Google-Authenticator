package me.maxih.googleauth.util

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}

import scala.collection.mutable
import scala.concurrent.duration.TimeUnit

/**
  * Created by Maxi H. on 28.04.2018
  */
object Scheduler {
  private val active = mutable.ListBuffer[Scheduler]()

  def shutdownAll(): Unit = active.foreach(_.shutdown())
  def awaitShutdownAll(timeout: Long, unit: TimeUnit = TimeUnit.SECONDS): Unit = active.foreach(_.awaitTermination(timeout, unit))
}


case class Scheduler(poolSize: Int = 1) {
  val delegate: ScheduledExecutorService = Executors.newScheduledThreadPool(poolSize)
  Scheduler.active += this


  def schedule(command: => Unit, delay: Long, unit: TimeUnit = TimeUnit.SECONDS): ScheduledFuture[Unit] =
    delegate.schedule[Unit](() => command, delay, unit)

  def scheduleAtRate(command: => Unit, initialDelay: Long, period: Long, unit: TimeUnit = TimeUnit.SECONDS): ScheduledFuture[_] =
    delegate.scheduleAtFixedRate(() => command, initialDelay, period, unit)

  def scheduleWithFixedDelay(command: => Unit, initialDelay: Long, delay: Long, unit: TimeUnit = TimeUnit.SECONDS): ScheduledFuture[_] =
    delegate.scheduleWithFixedDelay(() => command, initialDelay, delay, unit)

  def shutdown(): Unit = delegate.shutdown()

  def isShutdown: Boolean = delegate.isShutdown

  def awaitTermination(timeout: Long, unit: TimeUnit = TimeUnit.SECONDS): Unit = delegate.awaitTermination(timeout, unit)

}
