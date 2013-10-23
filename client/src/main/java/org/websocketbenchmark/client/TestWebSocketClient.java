package org.websocketbenchmark.client;

import java.io.IOException;

public class TestWebSocketClient extends TestClient implements TestClientEventListener  {
	
	private SocketIOClient driver;
	
	public TestWebSocketClient(SocketIOClient wscdriver) {
		// TODO Auto-generated constructor stub
		super();

		this.driver = wscdriver;
	}

	public void connect(){
		driver.connect();
	}
	
	public void onError(IOException e) {
		// TODO Auto-generated method stub
		
	}

	public void onMessage(String message) {
		// TODO Auto-generated method stub
		
	}

	public void onClose() {
		// TODO Auto-generated method stub
		
	}

	public void onOpen() {
		// TODO Auto-generated method stub
		
	}

	public void send(String msg) {
		// TODO Auto-generated method stub
		
	}

	public void sendTimestampedChat() {
		// TODO Auto-generated method stub
		driver.sendTimestampedChat();
	}

	public void close() throws IOException{
		// TODO Auto-generated method stub
		driver.close();
	}

}
