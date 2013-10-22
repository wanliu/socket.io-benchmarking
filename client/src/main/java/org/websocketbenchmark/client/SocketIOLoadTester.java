package org.websocketbenchmark.client;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.commons.cli2.Argument;
import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.cli2.util.HelpFormatter;

public class SocketIOLoadTester extends Thread implements
		SocketIOClientEventListener {

	public static final int STARTING_MESSAGES_PER_SECOND_RATE = 1;
	public static final int SECONDS_TO_TEST_EACH_LOAD_STATE = 10;

	public static final int SECONDS_BETWEEN_TESTS = 2;

	public static final int MESSAGES_RECEIVED_PER_SECOND_RAMP = 100;

	public static final int POST_TEST_RECEPTION_TIMEOUT_WINDOW = 5000;

	protected int[] concurrencyLevels = { 1, 10, 25, 50, 75, 100, 200, 300,
			400, 500, 750, 1000, 1250, 1500, 2000 };
	private static final int MAX_MESSAGES_PER_SECOND_SENT = 800;

	protected Set<TestClient> clients = new HashSet<TestClient>();

	protected int concurrency;

	protected int currentMessagesPerSecond = STARTING_MESSAGES_PER_SECOND_RATE;

	protected boolean lostConnection = false;

	protected Integer numConnectionsMade = 0;

	protected List<Long> roundtripTimes;

	private boolean postTestTimeout;

	private boolean testRunning;
	
	protected String vHost;
	
	private static String driver = "websocket";

	protected SocketIOLoadTester(String host, Integer port, ArrayList<Integer> concurrencies) {
		this.vHost = host + ':' + port;
		
		printInfo();
		
		if (concurrencies.size() > 0) {

			System.out.print("Using custom concurrency levels: ");
			this.concurrencyLevels = new int[concurrencies.size()];

			int i = 0;
			for (Integer concurrency : concurrencies) {
				this.concurrencyLevels[i] = concurrency.intValue();
				i++;

				System.out.print(concurrency + " ");
			}

			System.out.println();
		}
		

	}
	
	public void printInfo() {
		System.out.printf("Test for %s\n", this.vHost);
		System.out.printf("use %s driver\n", SocketIOLoadTester.driver);		
	}

	@Override
	public synchronized void run() {

		BufferedWriter f = null;
		try {
			f = new BufferedWriter(new FileWriter(System.currentTimeMillis()
					+ ".log"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < concurrencyLevels.length; i++) {
			this.concurrency = concurrencyLevels[i];

			// Reset the failure switches.
			this.lostConnection = false;
			this.postTestTimeout = false;

			System.out.println("---------------- CONCURRENCY "
					+ this.concurrency + " ----------------");
			// This won't return until we get an ACK on all the connections.
			this.numConnectionsMade = 0;
			this.makeConnections(this.concurrency);

			Map<Double, SummaryStatistics> summaryStats = this
					.performLoadTest();

			// shutdown all the clients
			for (TestClient c : this.clients) {
				try {
					c.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			for (Double messageRate : summaryStats.keySet()) {
				SummaryStatistics stats = summaryStats.get(messageRate);

				try {
					f.write(String.format("%d,%f,%d,%f,%f,%f,%f\n",
							this.concurrency, messageRate, stats.getN(),
							stats.getMin(), stats.getMean(), stats.getMax(),
							stats.getStandardDeviation()));
					System.out.println("Wrote results of run to disk.");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		try {
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return;
	}

	protected void makeConnections(int numConnections) {
		// Start the connections. Wait for all of them to connect then we go.
		this.clients.clear();

		for (int i = 0; i < this.concurrency; i++) {
			TestClient client = factoryClient( SocketIOLoadTester.driver, vHost, this);
//			SocketIOClient client = new SocketIOClient(
//					SocketIOClient.getNewSocketURI(vHost),
//					this);
			this.clients.add(client);
			client.connect();
		}

		try {
			this.wait();
		} catch (InterruptedException e) {
			System.out.println("Interrupted!");
		}
		System.out.println("Woken up - time to start load test!");
	}
	
	protected TestClient factoryClient(String driverName, String host, SocketIOLoadTester listener){
		TestClient client;
		
		if (driverName.equals("websocket")) 
		{
			SocketIOClient wscdriver = new SocketIOClient(SocketIOClient.getNewSocketURI(host), listener);
			client = new TestWebSocketClient(wscdriver);
		}
		else if (driverName.equals("xhr-polling"))
		{
			client = new TestXHRPollingClient(TestXHRPollingClient.getNewSocketURI(host), listener);
		} 
		else 
		{
			client = null;
		}
		
		return client;
	}
	
    protected Map<Double, SummaryStatistics> performLoadTest() {
		// Actually run the test.
		// Protocol is spend 3 seconds at each load level, then ramp up messages
		// per second.
		Map<Double, SummaryStatistics> statisticsForThisConcurrency = new HashMap<Double, SummaryStatistics>();

		this.testRunning = true;

		// TODO Think about having this vary as an initial condition thing - for
		// lower concurrencies, starting at 1 costs us a lot fo time to run the
		// test.
		this.currentMessagesPerSecond = STARTING_MESSAGES_PER_SECOND_RATE;
		double effectiveRate = 0;
		double overallEffectiveRate = 0;

		while (!this.lostConnection && !this.postTestTimeout
				&& currentMessagesPerSecond < MAX_MESSAGES_PER_SECOND_SENT) {
			System.out.print(concurrency + " connections at "
					+ currentMessagesPerSecond + ": ");

			this.roundtripTimes = new ArrayList<Long>(
					SECONDS_TO_TEST_EACH_LOAD_STATE * currentMessagesPerSecond);

			for (int i = 0; i < SECONDS_TO_TEST_EACH_LOAD_STATE; i++) {
				effectiveRate = this.triggerChatMessagesOverTime(
						currentMessagesPerSecond, 1000);
				overallEffectiveRate += effectiveRate;
			}

			overallEffectiveRate = overallEffectiveRate
					/ SECONDS_TO_TEST_EACH_LOAD_STATE;
			System.out.print(String
					.format(" rate: %.3f ", overallEffectiveRate));

			// At this point, all messages have been sent so we should wait
			// until they've all been received.
			this.postTestTimeout = true;
			synchronized (this) {
				try {
					this.wait(POST_TEST_RECEPTION_TIMEOUT_WINDOW);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			if (this.postTestTimeout) {
				System.out.println(" failed - not all messages received in "
						+ POST_TEST_RECEPTION_TIMEOUT_WINDOW + "ms");
			} else {
				// Grab and store the summary statistics for this run.
				statisticsForThisConcurrency.put(overallEffectiveRate,
						this.processRoundtripStats());

				// TODO Do a check here - if we saw a mean roundtrip time above
				// 100ms or so, that's the congestion point and we should record
				// that as the "knee" of the curve, basically.
			}

			// Make sure to always increase by at least 1 message per second.
			currentMessagesPerSecond += Math.max(1,
					MESSAGES_RECEIVED_PER_SECOND_RAMP / this.concurrency);

			try {
				Thread.sleep(SECONDS_BETWEEN_TESTS * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		this.testRunning = false;
		return statisticsForThisConcurrency;
	}

	protected double triggerChatMessagesOverTime(int totalMessages, long ms) {
		long startTime = System.currentTimeMillis();

		long baseMsPerSend = ms / totalMessages;
		long adjustedMsPerSend = baseMsPerSend;

		long delta = 0;

		// We basically want to guarantee that this method is going to take
		// exactly a second to run, so the messages are spread out across the
		// full second.

		Iterator<TestClient> clientsIterator = this.clients.iterator();
		for (int i = 0; i < totalMessages; i++) {
			long messageStartTime = System.currentTimeMillis();

			TestClient client = clientsIterator.next();
			client.sendTimestampedChat();

			if (!clientsIterator.hasNext()) {
				clientsIterator = clients.iterator();
			}

			// how long we want it to take how long it took to send this message
			delta = adjustedMsPerSend
					- (System.currentTimeMillis() - messageStartTime);

			// If delta is positive, then we have time to spare before the next
			// message. Sleep it off.
			if (delta >= 0) {
				try {

					// This is for the weird situation when system time ticks
					// between the test and the assignment.
					// We split them for accuracy, but if it goes negative,
					// sleep throws an exception.
					if (delta > 0) {
						Thread.sleep(delta);
					}

					// If we came in under the limit, then figure the sleep put
					// us back on target and
					// we should default to the normal duration window.
					adjustedMsPerSend = baseMsPerSend;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// If we end up in this branch, it's because it took LONGER to
				// send than the time we have allocated.
				// Basically, if this happens more than once or twice it means
				// we're sending as fast as possible
				// and we shouldn't be delaying at all. The problem with this is
				// that in a lot of cases this means we can't
				// actually cause the server to jam because we're not moving
				// fast enough.

				// Keep track of how many ms behind we were.
				System.err.print(delta + " ");

				// now adjust the time for the next one. delta will be negative
				// if we're in this branch
				adjustedMsPerSend = baseMsPerSend + delta;

				// Clamp it at 0. Once we hit that point it'll basically just
				// scream
				if (adjustedMsPerSend < 0)
					adjustedMsPerSend = 0;
			}
		}

		// Saw a little bit of drift here, but I'm going to say it's okay for
		// now. Should take a look at it later. Didn't seem 100% monotonically
		// increasing
		// although I did see some general positive drift as the number of
		// messages increased. Might have to do with integer wait values and
		// rounding?
		// System.out.println("Time duration at end: " +
		// (System.currentTimeMillis() - timeAtStart) + " (target: " + ms +
		// ")");

		long duration = System.currentTimeMillis() - startTime;
		return new Integer(totalMessages).doubleValue() / ((duration) / 1000.0);
	}

	protected SummaryStatistics processRoundtripStats() {
		SummaryStatistics stats = new SummaryStatistics();

		for (Long roundtripTime : this.roundtripTimes) {
			stats.addValue(roundtripTime);
		}

		System.out
				.format(" n: %5d min: %8.0f  mean: %8.0f   max: %8.0f   stdev: %8.0f\n",
						stats.getN(), stats.getMin(), stats.getMean(),
						stats.getMax(), stats.getStandardDeviation());

		return stats;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Just start the thread.
		String host = "";
		Integer port = 8080;
		ArrayList<Integer> concurrencies = new ArrayList<Integer>();
		
		CommandLine cl = generateCommandSettings(args);
		
 		if (cl == null) {
 			System.exit(-1);
 		}
 		
 		if (cl.hasOption("host")){
 			host = (String)cl.getValue("host");
 		}
 		
 		if (cl.hasOption("--port")){
 			port = Integer.parseInt((String)cl.getValue("--port"));
 		}
		
 		if (cl.hasOption("--concurrencies")){
 			String levelString = (String) cl.getValue("--concurrencies");
 			String[] levels = levelString.split("(,| )");
 			for (String level : levels){
 			    //doTarget(target);
 			    concurrencies.add(new Integer(level));
 			}
 		}
 		
 		if (cl.hasOption("--driver")){
 			driver = (String) cl.getValue("--driver");
 		} 
//		if (args.length > 0) {
//			// Assume all the arguments are concurrency levels we want to test
//			// at.
//
//			for (String arg : args) {
//				concurrencies.add(new Integer(arg));
//			}
//		}

		SocketIOLoadTester tester = new SocketIOLoadTester(host, port, concurrencies);
		tester.start();
	}
	
	protected static CommandLine generateCommandSettings(String[] args){
		DefaultOptionBuilder oBuilder = new DefaultOptionBuilder();
		ArgumentBuilder aBuilder = new ArgumentBuilder();
		GroupBuilder gBuilder = new GroupBuilder();

		Argument hostArgument = 
		    aBuilder
		        .withName("host")
		        .withMinimum(1)
		        .withMaximum(1)
		        .withDescription("remote server host name dns or ip")
		        .create();
		
		Argument portArgument = 
			aBuilder
				.withName("post")
				.withMinimum(1)
				.withMaximum(1)
				.create();
	
	    Argument concurrentLevelsArgument =
			aBuilder
				.withName("levels")
				.withMinimum(1)
				.withMaximum(1)				
				.create();
	    
	    Argument driverArgument = 
		    aBuilder
		    	.withName("driver")
		    	.withMinimum(1)
		    	.withMaximum(1)
		    	.create();
	   
		Option portOption = 
			oBuilder
				.withShortName("p")
				.withLongName("port")
				.withArgument(portArgument)
				.withDescription("remote server port number.(etc 8080)")
				.create();
		
		Option concurrenciesOption =
			oBuilder
				.withShortName("c")
				.withLongName("concurrencies")
				.withArgument(concurrentLevelsArgument)
				.withDescription("test start levels, [1,10,25,50,75,...2000] ")
				.create();
		
		Option driverOption = 
			oBuilder
				.withShortName("d")
				.withLongName("driver")
				.withArgument(driverArgument)
				.withDescription("use difference protocol driver.(etc websocket, xhr-polling)")
				.create();
		
		Group optionsGroup = 
			gBuilder
				.withName("options")
				.withOption(hostArgument)
				.withOption(portOption)
				.withOption(concurrenciesOption)
				.withOption(driverOption)
				.create();
				
		HelpFormatter hf = new HelpFormatter();

		// configure a parser
		Parser p = new Parser();
		p.setGroup(optionsGroup);
		p.setHelpFormatter(hf);
		p.setHelpTrigger("--help");
		
		return p.parseAndHelp(args);
	}
	

	public void onError(IOException e) {
		// TODO Auto-generated method stub

	}

	public void onMessage(String message) {
		// System.out.println("message: " + message);
	}

	public void onClose() {
		if (this.testRunning) {
			lostConnection = true;
			System.out.println(" failed!");
			System.out.println("Lost a connection. Shutting down.");
		}
	}

	public void onOpen() {
		synchronized (this) {
			numConnectionsMade++;
			if (numConnectionsMade.compareTo(concurrency) == 0) {
				System.out.println("All " + concurrency
						+ " clients connected successfully.");
				// Turn the main testing thread back on. We don't want to
				// accidentally
				// be executing on some clients main thread.
				this.notifyAll();
			}
		}
	}

	public void messageArrivedWithRoundtrip(long roundtripTime) {
		this.roundtripTimes.add(roundtripTime);

		if (this.roundtripTimes.size() == SECONDS_TO_TEST_EACH_LOAD_STATE
				* currentMessagesPerSecond) {
			synchronized (this) {
				this.postTestTimeout = false;
				this.notifyAll();
			}
		}
	}

}
