package tools.other;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Contains methods which deal with the concurrent execution of methods */
public class Concurrency {

	/** Instance of logger */
	private static final Logger logger = LoggerFactory 
			.getLogger(Concurrency.class.getName());
	private static ExecutorService exec = Executors.newCachedThreadPool();
	

	public static void close() {

		try {
			exec.shutdown();
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
			exec = Executors.newCachedThreadPool();
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/** Prepares the concurrent execution of the current tasks */
	public static void executeConcurrently(Collection<Callable<Void>> tasks) {
		try {
			Thread.currentThread().setName("Concurrency execution");
			exec.invokeAll(tasks);
		} catch (final Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

}
