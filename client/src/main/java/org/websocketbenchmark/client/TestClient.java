package org.websocketbenchmark.client;

import java.io.IOException;

public class TestClient{

	protected static int nextId = 0;

	protected int id;
	
	public TestClient() {
		id = nextId;

		nextId++;
	}

	public void connect() {
		
	}

	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}

	public void sendTimestampedChat() {
		// TODO Auto-generated method stub
		
	}
}

