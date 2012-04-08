package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ScanList extends ListActivity {
	
	private static MulticastSocket msoc;
	
	static InetAddress mcastAddr = null;
	
	static int port = 5500;
	
	DatagramSocket socket;
	
	ArrayList<Map<String, String>> list;
	SimpleAdapter adapter;
	ListView lv;
	
	private volatile boolean listenerStatus = true;
	
	String scanned_name, scanned_addr, selectedAddr = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        scanReplyListener.start();
        sendScanRequest();
        
        lv = getListView();
        
        
		
		
		
    }
    
    public Handler scanHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		if(msg.what == 1) {
    			scanned_name = (String) msg.obj;
    		}
    		if(msg.what == 2) {
    			scanned_addr = (String) msg.obj;
    		}
    		
    		
    		
    		ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
    		
    		list.add(putData(scanned_name, scanned_addr));
    		
            String[] from = { "name", "address" };
    		int[] to = { android.R.id.text1, android.R.id.text2 };

    		SimpleAdapter adapter = new SimpleAdapter(getApplicationContext(), list,
    				android.R.layout.simple_list_item_2, from, to);
    		setListAdapter(adapter);
    		
    		lv.setOnItemClickListener(new OnItemClickListener() {
    		    public void onItemClick(AdapterView<?> parent, View view,int position, long id) { 
    		    	
    		    	TextView name_tv = (TextView) findViewById(android.R.id.text1);
    		    	TextView addr_tv = (TextView) findViewById(android.R.id.text2);
    		    	
    		    	selectedAddr = addr_tv.getText().toString();
    		    	
    	    		onBackPressed();    	    		
    	    		
    		    }
    		  
    		});
    		
    	}
    };
   
    
    
    
	private HashMap<String, String> putData(String name, String address) {
		HashMap<String, String> item = new HashMap<String, String>();
		item.put("name", name);
		item.put("address", address);
		return item;
	}
	
    
    public void sendScanRequest() {
    	
    	try {
			InetAddress mcastAddr = InetAddress.getByName("224.2.76.24");
			
			msoc = new MulticastSocket(port);
			
			msoc.joinGroup(mcastAddr);
			
			
			byte[] message = new byte[1024];
				
			message = "M".getBytes();
			
					
			DatagramPacket packet = new DatagramPacket(message, message.length,mcastAddr,port);
				
			msoc.send(packet);
			
			Log.d("SL","Multicast Query Sent");
			
    	} catch(IOException e) {
    		Log.e("SL", "IOException");
    	}
    	
    	
    }
    
    
    public void onBackPressed() {
    	listenerStatus = false;
    	socket.close();
    	if(socket.isClosed())
    		Log.d("SL","Socket closed");
    	Intent returnIntent = new Intent();
	    returnIntent.putExtra("selectedIP",selectedAddr);
	    setResult(RESULT_OK,returnIntent);        
    	finish();
    }
    
    
    //Thread to listen for replies to scanpacket
    
    Thread scanReplyListener = new Thread(new Runnable() {

		@Override
		public void run() {
						
			if(listenerStatus == true) {
			
			try {
				
				socket = new DatagramSocket(40004);
				Log.d("SL","Socket created");
				byte[] replyData = new byte[1024];
				DatagramPacket replyPacket = new DatagramPacket(replyData,replyData.length);
			
				while(listenerStatus == true) {
				
					
					socket.receive(replyPacket);
					
					Log.d("SL","AVAILABLE received");
					
					String replierName=new String(replyPacket.getData(),0,replyPacket.getLength());
						
					String senderAddr = replyPacket.getAddress().getHostAddress();
						
					Log.d("SL","Message: "+replierName+" from: "+senderAddr);
					
					//Toast.makeText(getApplicationContext(), "Message: "+replierName+" from: "+senderAddr, Toast.LENGTH_SHORT).show();
					
					//list.add(putData(replierName, senderAddr));
					
					Message name_msg = Message.obtain();
		        	name_msg.obj = replierName;
		        	name_msg.what = 1;
		        	scanHandler.sendMessage(name_msg);
		        	
		        	Message addr_msg = Message.obtain();
		        	addr_msg.obj = senderAddr;
		        	addr_msg.what = 2;
		        	scanHandler.sendMessage(addr_msg);
				}
				
				} catch (SocketException e) {
					Log.e("SL","SocketException");
			} catch (IOException e) {
				Log.e("SL","IOException");
			}
		}
		
		if(listenerStatus == false) {
			
		}
		}
	});
    
    
}
