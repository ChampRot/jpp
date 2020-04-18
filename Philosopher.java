package io.dama.ffi.parcoord.dining.cond;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Philosopher extends Thread implements IPhilosopher {

	private Philosopher left;
	private Philosopher right;
	private Lock table;
	private int seat;
	private volatile boolean stopped = false;
	private volatile boolean eating = false;
	private Condition eatingCondition;

	@Override
	public void setLeft(final IPhilosopher left) {
		this.left = (Philosopher) left;
	}

	@Override
	public void setRight(final IPhilosopher right) {
		this.right = (Philosopher) right;
	}

	@Override
	public void setSeat(final int seat) {
		this.seat = seat;
	}

	@Override
	public void setTable(final Lock table) {
		this.table = table;
		this.eatingCondition = this.table.newCondition();
	}

	@Override
	public void stopPhilosopher() {
		this.stopped = true;
		this.interrupt();
	}

	@Override
	public void run() {
		while (!this.isStopped()) {
			try {
				this.think();
				this.table.lock();
				while (this.left.isEating() || this.right.isEating()) {
					this.left.eatingCondition.await(PhilosopherExperiment.MAX_TAKING_TIME_MS, TimeUnit.MILLISECONDS);
					this.right.eatingCondition.await(PhilosopherExperiment.MAX_TAKING_TIME_MS, TimeUnit.MILLISECONDS);
				}
				this.table.unlock();
				this.eat();
				this.table.lock();
				this.eatingCondition.signalAll();
				this.table.unlock();
			} catch (InterruptedException e) {
				this.log("INTERRUPTED");
			} 
		}
		this.log("stopped");
	}

	private void eat() throws InterruptedException {
		this.eating = true;
		this.log("started eating");
		Thread.sleep(ThreadLocalRandom.current().nextInt(PhilosopherExperiment.MAX_EATING_DURATION_MS));
		this.log("stopped eating");
		this.eating = false;
	}

	private void think() throws InterruptedException {
		this.log("thinking");
		Thread.sleep(ThreadLocalRandom.current().nextInt(PhilosopherExperiment.MAX_THINKING_DURATION_MS));
	}

	private boolean isEating() {
		return this.eating;
	}

	private boolean isStopped() {
		return this.stopped;
	}

//	Zur Ausgabe einer "Tabelle"
	private void log(final String message) {
		synchronized (Philosopher.class) {
			int i;
			System.out.print("|");
			for (i = 1; i <= this.seat; i++) {
				System.out.print("                        |");
			}
			System.out.print(String.format("%1$25s", this.seat + " " + message + "|"));
			for (; i < PhilosopherExperiment.PHILOSOPHER_NUM; i++) {
				System.out.print("                        |");
			}
			System.out.println();
			for (i = 0; i < PhilosopherExperiment.PHILOSOPHER_NUM; i++) {
				System.out.print("+------------------------");
			}
			System.out.println("+");
		}
	}
}
