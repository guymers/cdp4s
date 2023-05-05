package cdp4s.chrome.interpreter

import cats.effect.kernel.Ref

import java.util.concurrent.atomic.AtomicLong
import cats.effect.Sync
import cats.syntax.functor._
import cdp4s.domain.model.Target.SessionID

object EventListenersPerSession {

  def create[F[_]](implicit F: Sync[F]): F[EventListenersPerSession[F]] = {
    Ref.of[F, Map[Option[SessionID], EventListeners[F]]](Map.empty).map { ref =>
      new EventListenersPerSession(ref)
    }
  }
}

final class EventListenersPerSession[F[_]] private (
  ref: Ref[F, Map[Option[SessionID], EventListeners[F]]],
)(implicit F: Sync[F]) {

  def listeners(id: Option[SessionID]): F[Iterable[EventListener[F]]] = {
    ref.get.map { m =>
      m.get(id).map(_.listeners).getOrElse(Iterable.empty)
    }
  }

  def addListener(id: Option[SessionID], listener: EventListener[F]): F[Long] = {
    ref.modify { m =>
      val listeners = m.getOrElse(id, EventListeners.unsafe[F]())
      val (newListeners, index) = listeners.add(listener)
      (m + (id -> newListeners), index)
    }
  }

  def removeListener(id: Option[SessionID], index: Long): F[Unit] = {
    ref.update { m =>
      m.get(id) match {
        case None => m
        case Some(listeners) => m + (id -> listeners.remove(index))
      }
    }
  }

  def removeListeners(id: Option[SessionID]): F[Unit] = {
    ref.update(_ - id)
  }
}

object EventListeners {

  def create[F[_]](implicit F: Sync[F]): F[EventListeners[F]] = F.delay(unsafe())

  def unsafe[F[_]](): EventListeners[F] = {
    val seq = new AtomicLong(0)
    new EventListeners(seq, Map.empty)
  }
}

final class EventListeners[F[_]] private (
  seq: AtomicLong,
  m: Map[Long, EventListener[F]],
) {
  def listeners: Iterable[EventListener[F]] = m.values

  def add(listener: EventListener[F]): (EventListeners[F], Long) = {
    val index = seq.incrementAndGet()
    (new EventListeners(seq, m + (index -> listener)), index)
  }

  def remove(index: Long): EventListeners[F] = {
    new EventListeners(seq, m - index)
  }
}
