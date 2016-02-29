package rabbit.lyra.internal.util.concurrent;

import rabbit.lyra.util.Duration;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A circuit that accepts re-entrant {@link #open()}, allows waiting threads to be interrupted, and
 * {@link #close()} calls and ensures fairness when releasing {@link #await() waiting} threads.
 * 
 * @author Jonathan Halterman
 */
public class ReentrantCircuit {
  private final Sync sync = new Sync();

  /**
   * Synchronization state of 0 = closed, 1 = open.
   */
  private static final class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 992522674231731445L;

    /**
     * Closes the circuit.
     */
    @Override
    public boolean tryReleaseShared(int ignored) {
      setState(0);
      return true;
    }

    /**
     * Opens the circuit if not a test.
     */
    @Override
    protected int tryAcquireShared(int acquires) {
      // Check to make sure the acquisition is not barging in front of a queued thread
      Thread queuedThread = getFirstQueuedThread();
      if (queuedThread != null && queuedThread != Thread.currentThread())
        return -1;

      // If await test
      if (acquires == 0)
        return isClosed() ? 1 : -1;

      // Explicit open call
      setState(1);
      return 1;
    }

    private boolean isClosed() {
      return getState() == 0;
    }

    private void open() {
      setState(1);
    }
  }

  /**
   * Waits for the circuit to be closed, aborting if interrupted.
   */
  public void await() throws InterruptedException {
    sync.acquireSharedInterruptibly(0);
  }

  /**
   * Waits for the {@code waitDuration} until the circuit has been closed, aborting if interrupted,
   * returning true if the circuit is closed else false.
   */
  public boolean await(Duration waitDuration) throws InterruptedException {
    return sync.tryAcquireSharedNanos(0, waitDuration.toNanos());
  }

  /**
   * Closes the circuit, releasing any waiting threads.
   */
  public void close() {
    sync.releaseShared(1);
  }

  /**
   * Interrupts waiting threads.
   */
  public void interruptWaiters() {
    for (Thread t : sync.getSharedQueuedThreads())
      t.interrupt();
  }

  /**
   * Returns whether the circuit is closed.
   */
  public boolean isClosed() {
    return sync.isClosed();
  }

  /**
   * Opens the circuit.
   */
  public void open() {
    sync.open();
  }

  @Override
  public String toString() {
    return isClosed() ? "closed" : "open";
  }
}
