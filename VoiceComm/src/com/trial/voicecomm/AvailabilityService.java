//Service that listens for multicast packets and replies to device enquiring availability


package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class AvailabilityService extends Service{
	
	//Need to maintain currentState somehow.
	
	SharedPreferences preferences;

	private volatile boolean clientStatus = true;
	
	InetAddress inet = null;
    
    MulticastSocket msoc = null;
    
    byte[] buffer = new byte[1024];
    
    int port = 1900;
    
    DatagramSocket socket;
    
    String ownIP,ownName;
	
	@Override
	public IBinder onBind(Intent arg0) {
		
		return null;
	}
	
	@Override
	public void onCreate() {
		
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		ownName = preferences.getString("username", "Unnamed");
		setOwnIP();
		clientStatus = true;
		mcastClient.start();
	}
	
	@Override
	public void onDestroy() {
		
		clientStatus = false;
	}
	
	Thread mcastClient = new Thread(new Runnable() {

		@Override
		public void run() {
			if(clientStatus == true) {
			try {
	        	inet = InetAddress.getByName("239.255.255.250");
	        	Log.d("AS","Mcast Address retrieved");
	        	
	        	DatagramPacket packet = new DatagramPacket(buffer, buffer.length,inet,port);
	        	
	        	msoc = new MulticastSocket(port);
	        	Log.d("AS","McastSocket created");
	        	
	        	msoc.joinGroup(inet);
	        	Log.d("AS","multicast group joined");
	        	
	        	while(clientStatus) {
	        		msoc.receive(packet);
	        		String msgStr = new String(packet.getData(),0,packet.getLength()); 
	        	
	        		if(msgStr.equals("vow_scan")) {

	        		String senderAddr = (packet.getAddress()).getHostAddress();
	        		Log.d("AS","Availability query from: "+senderAddr);
	        	      	
	        		socket = new DatagramSocket();
				
	        		//Sending own name to source of multicast
	        		InetAddress senderInet = InetAddress.getByName(senderAddr);
	        		String sender = senderInet.getHostAddress();
	        		Log.d("AS","Mcast source is "+sender);
	        		if(!ownIP.equals(sender)) {
	        			byte[] sendData = (ownName).getBytes();
	        			DatagramPacket sendPacket = new DatagramPacket (sendData, sendData.length,senderInet,40004);
	        			socket.send(sendPacket);
	        			Log.d("AS","AVAILABLE Sent to "+senderInet);
	        			}
	        		else {
	        			Log.d("AS", "I myself am the source of multicast!");
	        		}
	        		
	        		}
	        	}
	        					
	        } catch(UnknownHostException e) {
	        	e.printStackTrace();
	        } catch (IOException e) {
				e.printStackTrace();
			}
			}
			
			
		
				
		if(clientStatus == false) {
			try {
				Log.d("AS","Thread Stopped");
	            msoc.leaveGroup(inet);
	            Log.d("AS","multicast group left");
	            
	            msoc.close();
	            socket.close();
	        } catch (IOException e) {
	            Log.e("AS", "IOException while leaving group");
	        }
		}
		}
    });
	
public void setOwnIP() {
 	   
        
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        ownIP = inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("MD", e.toString());
        }
        
        
       } 
    
	
	

}
