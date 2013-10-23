package org.websocketbenchmark.client;

import java.io.IOException;

public interface TestClientEventListener {
	
	public void onError(IOException e);

	public void onMessage(String message);

	public void onClose();

	public void onOpen();
	
	public void connect();
	
	public void send(String msg) throws IOException;
	
	public void sendTimestampedChat();
	
	public void close() throws IOException;
	
}
