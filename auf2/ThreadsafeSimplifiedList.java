package io.dama.ffi.hoh;

import java.util.concurrent.locks.StampedLock;

public class ThreadsafeSimplifiedList<T> implements SimplifiedList<T> {
	/*
	 * Statt *synchronized* als Schlüsselwort an den Methoden wird hier eine private
	 * Instanzvariable zum Synchronisieren verwendet, damit niemand von außen an
	 * derselben Variable einen Lock setzen kann, um Verklemmungen zu vermeiden.
	 *
	 */
//	Eigenes Lock für den Kopf der Liste ('first'), da dieser null sein könnte.
//	StampedLock da hauptsächlich gelesen wird
//	könnte auch für eine addFirst Methode benutzt werden
	private final StampedLock headLock;
	
	private Node<T> first;

	private class Node<U> {
		private U element;
		private final Node<U> prev;
		private Node<U> next;
//		Lock Objekt für ein Node, StampedLock, da auch Nodes meist nur lesend 
//		verwendet werden (beim durchlaufen der Liste)
		private final StampedLock lock;

		private Node(final U element, final Node<U> prev, final Node<U> next) {
			super();
			this.element = element;
			this.prev = prev;
			this.next = next;
			this.lock = new StampedLock();
		}
	}

	public ThreadsafeSimplifiedList() {
		super();
		this.first = null;
		this.headLock = new StampedLock();
	}

	@Override
	public T get(final int i) {
//		Das Vorgehen hier findet sich in den anderen Methoden ebenfalls wieder:

		if(this.isEmpty()) {
			return null;
		}
//		Als erstes versuchen ob man "einfach so" lesen kann
		var currentStamp = this.first.lock.tryOptimisticRead();
		var ptr = this.first;
//		falls nicht => auf readLock warten
		if (!this.first.lock.validate(currentStamp)) {
			currentStamp = this.first.lock.readLock();
			ptr = this.first;
		}
		for (var j = 0; j < i; j++) {
//			Stamp merken
			var prevStamp = currentStamp;
//			aktuellen Pointer merken
			var prevPtr = ptr;
//			"einfach so" lesen?
			currentStamp = ptr.next.lock.tryOptimisticRead();
			ptr = ptr.next;
			try {
				if (!prevPtr.next.lock.validate(currentStamp)) {
//					falls "einfach so" nicht geklappt hat
					currentStamp = prevPtr.next.lock.readLock();
					ptr = prevPtr.next;
				}
			} finally {
//				Falls wir den letzten Knoten "schließen" mussten
				if (prevPtr.lock.isReadLocked()) {
					prevPtr.lock.unlockRead(prevStamp);
				}
			}
		}
		try {
			return delay(ptr.element);
		} finally {
//			Falls der letzte Knoten gelocked ist
			if (ptr.lock.isReadLocked()) {
				ptr.lock.unlockRead(currentStamp);
			}
		}
	}

	@Override
	public boolean add(final T e) {
		if (this.isEmpty()) {
			var currentStamp = this.headLock.writeLock();
			try {
				this.first = new Node<>(e, null, null);
			} finally {
				this.headLock.unlockWrite(currentStamp);
			}
		} else {
			var currentStamp = this.first.lock.tryOptimisticRead();
			var ptr = this.first;
			if (!this.first.lock.validate(currentStamp)) {
				currentStamp = this.first.lock.readLock();
				ptr = this.first;
			}
			while (ptr.next != null) {
				var prevStamp = currentStamp;
				var prevPtr = ptr;
				currentStamp = ptr.next.lock.tryOptimisticRead();
				ptr = ptr.next;
				try {
					if (!prevPtr.next.lock.validate(currentStamp)) {
						currentStamp = prevPtr.next.lock.readLock();
						ptr = prevPtr.next;
					}
				} finally {
					if (prevPtr.lock.isReadLocked()) {
						prevPtr.lock.unlockRead(prevStamp);
					}
				}
			}
			try {
				currentStamp = ptr.lock.tryConvertToWriteLock(currentStamp);
				if (currentStamp == 0) {
					currentStamp = ptr.lock.writeLock();
				}
				ptr.next = new Node<>(e, ptr, null);
			} finally {
				ptr.lock.unlockWrite(currentStamp);
			}
		}
		return true;
	}

	@Override
	public T set(final int i, final T e) {
		if(this.isEmpty()) {
			return null;
		}
		var currentStamp = this.first.lock.tryOptimisticRead();
		var ptr = this.first;
		if (!this.first.lock.validate(currentStamp)) {
			currentStamp = this.first.lock.readLock();
			ptr = this.first;
		}
		for (var j = 0; j < i; j++) {
			var prevStamp = currentStamp;
			var prevPtr = ptr;
			currentStamp = ptr.next.lock.tryOptimisticRead();
			try {
				ptr = ptr.next;
				if (!prevPtr.next.lock.validate(currentStamp)) {
					currentStamp = prevPtr.next.lock.readLock();
					ptr = prevPtr.next;
				}
			} finally {
				if (prevPtr.lock.isReadLocked()) {
					prevPtr.lock.unlockRead(prevStamp);
				}
			}
		}
		try {
			currentStamp = ptr.lock.tryConvertToWriteLock(currentStamp);
			if (currentStamp == 0) {
				currentStamp = ptr.lock.writeLock();
			}
			ptr.element = e;
			return e;
		} finally {
			ptr.lock.unlockWrite(currentStamp);
		}
	}

	@Override
	public boolean isEmpty() {
		var stamp = this.headLock.tryOptimisticRead();
		if (this.headLock.validate(stamp)) {
			return this.first == null;
		}
		try {
			stamp = this.headLock.readLock();
			return this.first == null;
		} finally {
			this.headLock.unlockRead(stamp);
		}
	}
}
