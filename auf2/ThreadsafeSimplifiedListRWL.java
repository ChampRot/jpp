package io.dama.ffi.hoh;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadsafeSimplifiedListRWL<T> implements SimplifiedList<T> {
	/*
	 * Statt *synchronized* als Schlüsselwort an den Methoden wird hier eine private
	 * Instanzvariable zum Synchronisieren verwendet, damit niemand von außen an
	 * derselben Variable einen Lock setzen kann, um Verklemmungen zu vermeiden.
	 *
	 */
	private final ReentrantReadWriteLock headLock;
	private Node<T> first;
	
	public static AtomicInteger delayCt = new AtomicInteger();

	private class Node<U> {
		private U element;
		private final Node<U> prev;
		private Node<U> next;

		private final ReentrantReadWriteLock lock;

		private Node(final U element, final Node<U> prev, final Node<U> next) {
			super();
			this.element = element;
			this.prev = prev;
			this.next = next;
			this.lock = new ReentrantReadWriteLock();
		}
	}

	public ThreadsafeSimplifiedList() {
		super();
		this.headLock = new ReentrantReadWriteLock();
		this.first = null;
	}

	@Override
	public T get(final int i) {
		Node<T> ptr;
//		Sperre zugriff auf Kopf der Liste (Attribut first der Klasse)
		try {
			this.headLock.readLock().lock();
			ptr = this.first;
		} finally {
			this.headLock.readLock().unlock();
		}
		try {
//			Sperre aktuellen Node
			ptr.lock.readLock().lock();
			for (var j = 0; j < i; j++) {
				try {
//				und nachfolgenden Node
					ptr.next.lock.readLock().lock();
					ptr = ptr.next;
				} finally {
//				entsperre vorigen Node wieder
					ptr.prev.lock.readLock().unlock();
				}
			}
			ThreadsafeSimplifiedList.delayCt.incrementAndGet();
			return delay(ptr.element);
		} finally {
			ptr.lock.readLock().unlock();
		}
	}

	@Override
	public boolean add(final T e) {
		if (this.isEmpty()) {
			try {
				this.headLock.writeLock().lock();
				this.first = new Node<>(e, null, null);
			} finally {
				this.headLock.writeLock().unlock();
			}
		} else {
			Node<T> ptr;
			try {
				this.headLock.readLock().lock();
				ptr = this.first;
			} finally {
				this.headLock.readLock().unlock();
			}
			try {
				ptr.lock.readLock().lock();
				while (ptr.next != null) {
					try {
						ptr.next.lock.readLock().lock();
						ptr = ptr.next;
					} finally {
						ptr.prev.lock.readLock().unlock();
					}
				}
			} finally {
				ptr.lock.readLock().unlock();
			}
			try {
				ptr.lock.writeLock().lock();
				ptr.next = new Node<>(e, ptr, null);
			} finally {
				ptr.lock.writeLock().unlock();
			}
		}
		return true;
	}

	@Override
	public T set(final int i, final T e) {
		Node<T> ptr;
		try {
			this.headLock.readLock().lock();
			ptr = this.first;
		} finally {
			this.headLock.readLock().unlock();
		}
		try {
			ptr.lock.readLock().lock();
			for (var j = 0; j < i; j++) {
				try {
					ptr.next.lock.readLock().lock();
					ptr = ptr.next;
				} finally {
					ptr.prev.lock.readLock().unlock();
				}
			}
		} finally {
			ptr.lock.readLock().unlock();
		}
		try {
			ptr.lock.writeLock().lock();
			ptr.element = e;
		} finally {
			ptr.lock.writeLock().unlock();
		}
		return e;
	}

	@Override
	public boolean isEmpty() {
		try {
			this.headLock.readLock().lock();
			return this.first == null;
		} finally {
			this.headLock.readLock().unlock();
		}
	}

}
