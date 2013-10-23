package org.websocketbenchmark.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;


public class TestXHRPollingClient extends TestClient implements TestClientEventListener  {

	private URI server;
	
	private SocketIOLoadTester listener;
	private RequestConfig requestConfig;
	
	CloseableHttpAsyncClient waitHttpClient;
	private Boolean connected = false;
	
	public TestXHRPollingClient(URI host,
			SocketIOLoadTester listener) {
		super();
		this.listener = listener;

		server = host;
		
		requestConfig = RequestConfig.custom()
		        .setSocketTimeout(20000)
		        .setConnectTimeout(20000).build();		
		
	}
	
	public void processWait() {
		if (waitHttpClient != null) {
			try {
				waitHttpClient.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		waitHttpClient = getClient();
		waitHttpClient.start();
        HttpGet httpget = new HttpGet(server);
        waitHttpClient.execute(httpget, dispatchCallback(waitHttpClient));		
	}
	
	public void log(String message) {
		System.out.println(message);
	}
	
	public List<String> decodePayload(String data) {
		List<String> ret = new ArrayList<String>();;
		if (data == null || data.isEmpty()) {
			 return ret;
		}
		
		String pLen = "";
		int length = 0;
		
		if (data.charAt(0) == '\ufffd') {
			for (int i = 1; i < data.length(); i++) {
				if (data.charAt(i) == '\ufffd') {
					length = Integer.parseInt(pLen);
					ret.add(data.substring(i + 1, i + 1 + length));
					i += length + 1;
					pLen = "";
				} else {
					pLen += data.charAt(i);
				}
			}
	
		} else {
			ret.add(data);
		}
		
		return ret;
	}
	
	public FutureCallback<HttpResponse> dispatchCallback(final CloseableHttpAsyncClient httpclient) {
		final TestXHRPollingClient self = this;
		
		return new FutureCallback<HttpResponse>() {

            public void completed(final HttpResponse response) {

            	if (response.getEntity().getContentLength() > 0){
            		HttpEntity entity = response.getEntity();
            		StringBuilder sb = new StringBuilder();
	            	try {
	            		BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), "UTF-8")); 
	            		String line = null;  
	            		while ((line = reader.readLine()) != null) {
	            			sb.append(line);
	            		}  
	            	}
	            	
	            	catch (IOException e) { e.printStackTrace(); }
	            	catch (Exception e) { e.printStackTrace(); }
//	            	log(sb.toString());
	            	List<String> messages = self.decodePayload(sb.toString());
	            	
//	            	System.out.printf("#%d", messages.size());
	            	for (String m : messages) {
	            		self.onMessage(m);
	            	}
	            	
	            	self.processWait();
            	}
            	
            	try {
					httpclient.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
           	
            }
           
            public void failed(final Exception ex) {
            	if (ex instanceof IOException){
            		self.onError((IOException) ex);
            	}
                System.out.println(ex.getMessage());
            }

            public void cancelled() {
                System.out.println(" cancelled");
                self.onClose();
            }	
			
		};
	}
	
	public CloseableHttpAsyncClient getClient(){
		
//		if (httpclient != null)  {
//			try {
//				httpclient.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
//		}
//		
		CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
	            .setDefaultRequestConfig(requestConfig)
	            .build();	
	
		return httpclient;
					
	}
	
	
	public void connect() {
		final CloseableHttpAsyncClient httpclient = getClient();
//		CloseableHttpClient httpclient = HttpClients.createDefault();
		final TestXHRPollingClient self = this;
		try {
			
//			ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
//
//                public String handleResponse(
//                        final HttpResponse response) throws ClientProtocolException, IOException {
//                    int status = response.getStatusLine().getStatusCode();
//                    if (status >= 200 && status < 300) {
//                        HttpEntity entity = response.getEntity();
//                        return entity != null ? EntityUtils.toString(entity) : null;
//                    } else {
//                        throw new ClientProtocolException("Unexpected response status: " + status);
//                    }
//                }
//
//            };
			httpclient.start();
            HttpGet httpget = new HttpGet(server);
            
//            httpclient.execute(httpget, dispatchCallback(httpclient));
            
            httpclient.execute(httpget, new FutureCallback<HttpResponse>() {
	            public void completed(final HttpResponse response) {

	            	if (response.getEntity().getContentLength() > 0){
	            		HttpEntity entity = response.getEntity();
		            	StringBuilder sb = new StringBuilder();
		            	try {
		            	    BufferedReader reader = 
		            	           new BufferedReader(new InputStreamReader(entity.getContent()), 65728);
		            	    String line = null;
		
		            	    while ((line = reader.readLine()) != null) {
		            	        sb.append(line);
		            	    }
		            	}
		            	
//            			            	response.getClass()
		            	catch (IOException e) { e.printStackTrace(); }
		            	catch (Exception e) { e.printStackTrace(); }
		            	
		            	self.onMessage(sb.toString());
	            	}
	            	
	            	try {
						httpclient.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            }

	            public void failed(final Exception ex) {
	            	if (ex instanceof IOException){
	            		self.onError((IOException) ex);
	            	}
	                System.out.println(ex.getMessage());
	            }

	            public void cancelled() {
	                System.out.println(" cancelled");
	                self.onClose();
	            }	
				
			});	           
//            String response = httpclient.execute(httpget, responseHandler); 
            
//            this.onMessage(response);
		}
		finally {
//			try {
//				httpclient.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        }
	}

	public void onError(IOException e) {
		// TODO Auto-generated method stub
		this.listener.onClose();
	}

	public void onMessage(String message) {
		long messageArrivedAt = Calendar.getInstance().getTimeInMillis();
//		log(message);
		switch (message.toCharArray()[0]) {
		case '1':
			this.connected = true;
			this.listener.onOpen();
			this.processWait();
			break;
		case '2':
			this.heartbeat();
			break;
		case '5':
//			log(Character.toString(message.toCharArray()[0]));	
			// We want to extract the actual message. Going to hack this shit.
			String[] messageParts = message.split(":");
			String lastPart = messageParts[messageParts.length - 1];
			String chatPayload = lastPart.substring(1, lastPart.length() - 4);

			long roundtripTime;
			String[] payloadParts = chatPayload.split(",");
			if (new Integer(this.id).toString().compareTo(payloadParts[0]) == 0) {
				roundtripTime = messageArrivedAt - new Long(payloadParts[1]);
				this.listener.messageArrivedWithRoundtrip(roundtripTime);
			}
			this.listener.onMessage(chatPayload);

			break;
		}
		
//		this.notify();
		
	}

	private void heartbeat() {
		try {
			this.send("2:::");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void onClose() {
		this.listener.onClose();
		try {
			this.waitHttpClient.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// TODO Auto-generated method stub
		
	}

	public void onOpen() {
		// TODO Auto-generated method stub
		
	}

	public void send(String msg) throws IOException{
//		final CloseableHttpAsyncClient httpclient = getClient();
		CloseableHttpClient httpclient = HttpClients.createDefault();
		final TestXHRPollingClient self = this;
		
		try {
			Long timestamp = Calendar.getInstance().getTimeInMillis();
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
			nameValuePairs.add(new BasicNameValuePair("t", timestamp.toString()));	
			URI uri = new URIBuilder(server)
					.addParameters(nameValuePairs)
					.build();
			HttpPost httppost = new HttpPost(uri);
			StringEntity msgEntity = new StringEntity(msg);
//			httpclient.start();
			System.out.print(">");

			httppost.setEntity(msgEntity);
			httppost.setHeader("Content-Type", "text/plain;charset=UTF-8");
//			httpclient.execute(httppost, new FutureCallback<HttpResponse>() {
//
//	            public void completed(final HttpResponse response) {
//
//	            	if (response.getEntity().getContentLength() > 0){
//	            		HttpEntity entity = response.getEntity();
//		            	StringBuilder sb = new StringBuilder();
//		            	try {
//		            	    BufferedReader reader = 
//		            	           new BufferedReader(new InputStreamReader(entity.getContent()), 65728);
//		            	    String line = null;
//		
//		            	    while ((line = reader.readLine()) != null) {
//		            	        sb.append(line);
//		            	    }
//		            	}
//		            	
////		            	response.getClass()
//		            	catch (IOException e) { e.printStackTrace(); }
//		            	catch (Exception e) { e.printStackTrace(); }
//		            	
//		            	if (sb.toString().equals("1")) {
//		           		
//		            		self.processWait();
//		            	}
//		            	
//	            	}
//	            	
//	            	try {
//						httpclient.close();
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//	            }
//
//	            public void failed(final Exception ex) {
//	            	if (ex instanceof IOException){
//	            		self.onError((IOException) ex);
//	            	}
//	                System.out.println(ex.getMessage());
//	            }
//
//	            public void cancelled() {
//	                System.out.println(" cancelled");
//	                self.onClose();
//	            }	
//				
//			});	
			
			
			ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };			
			String response = httpclient.execute(httppost, responseHandler);
			
//			System.out.println(response);
//			this.onMessage(response);
			httpclient.close();
		} catch (HttpHostConnectException e) {
			this.onClose();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	
	}

	public void chat(String message) {
		try {
			String fullMessage = "5:::{\"name\":\"chat\", \"args\":[{\"text\":\""
					+ message + "\"}]}";
			this.send(fullMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendTimestampedChat() {
		String message = this.id + ","
				+ new Long(Calendar.getInstance().getTimeInMillis()).toString();
		this.chat(message);
	}

	public void hello() {
		try {
			this.send("5:::{\"name\":\"hello\", \"args\":[]}");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void close() {
		try {
			this.send("0::");
			this.onClose();
		} catch (IOException e) {
			e.printStackTrace();
		}	
		// TODO Auto-generated method stub
		
	}
	
	public static URI getNewSocketURI(String server) {
		try {
			URL url = new URL("http://" + server + "/socket.io/1/");
			HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST");

			DataOutputStream wr = new DataOutputStream(
					connection.getOutputStream());
			wr.flush();
			wr.close();

			BufferedReader rd = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			String line = rd.readLine();
			String hskey = line.split(":")[0];
			return new URI("http://" + server + "/socket.io/1/xhr-polling/" + hskey);
		} catch (Exception e) {
			System.out.println("error: " + e);
			return null;
		}
	}
}
