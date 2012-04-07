
package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;



public class VoiceCommActivity extends Activity {
    


    public int port = 40005;			//which port??

	public int vport = 50005;
	
	public String ownIP, ownName;
		
	private DatagramSocket listenerSocket;
	
	private DatagramSocket requestSocket;

	private AudioManager am;

	public static CurrentState state;

	public String connectedTo;

	public boolean response;			//Receiver's response to incoming call. true = accept. false = reject

	private volatile boolean clThreadStatus ;
	
	public Object lock;					//for synchronized block used for wait/notify
	
	
	WifiManager wm;
    
    WifiManager.MulticastLock multicastLock;
	

	//UI Elements
	private EditText targetIP;
	private Button callButton,endButton,exitButton,scanButton;
	private TextView connStatus,deviceIP,user;

	AlertDialog.Builder incomingBuilder; 

	VoiceSender vs;
	VoiceReceiver vr;

	Thread sender,receiver;


	SharedPreferences preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        
        wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("myLock");
        
        multicastLock.acquire();			//lock automatically released when app exits/crashes
        Log.d("MD","multicast lock acquired");
        
        
        //Starting the service that listens for multicast packets
        startService(new Intent(VoiceCommActivity.this,AvailabilityService.class));
        
        findViewById(R.id.device_IP_label);
        findViewById(R.id.user_label);
        user = (TextView) findViewById(R.id.user);
        deviceIP = (TextView) findViewById(R.id.device_IP);
        targetIP = (EditText) findViewById(R.id.target_ip);
        scanButton = (Button) findViewById(R.id.scan_button);
        callButton = (Button) findViewById(R.id.call_button);
        endButton = (Button) findViewById(R.id.end_button);
        exitButton = (Button) findViewById(R.id.exit_button);
        connStatus = (TextView) findViewById (R.id.conn_status);
                
        scanButton.setOnClickListener(scanListener);
        callButton.setOnClickListener(callListener);
        endButton.setOnClickListener(endListener);
        exitButton.setOnClickListener(exitListener);
        
        state = CurrentState.AVAILABLE;
        
        setOwnIP();
        
        ownName = preferences.getString("username", "Name Not Set");
        
        user.setText(ownName);
        
        endButton.setEnabled(false);
         
        lock = new Object();
        
        
        //AlertDialog for incoming call
        incomingBuilder = new AlertDialog.Builder(this);
        incomingBuilder.setCancelable(false)
        		.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
        				@Override
        				public void onClick(DialogInterface arg0, int arg1) {
        					response = true;
        					synchronized(lock) {
        						lock.notifyAll();
        					}
        				}
        			})
        		.setNegativeButton("Reject", new DialogInterface.OnClickListener() {
        				@Override
        				public void onClick(DialogInterface arg0, int arg1) {
        					response = false;
        					synchronized(lock) {
        						lock.notifyAll();
        					}
        				}
        			});
        
        
        //Thread that listens for incoming connection requests
        clThreadStatus = true;
        ConnectionListener.start();
        
      
        
        
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_CALL);
        am.setSpeakerphoneOn(false);
        
               
    }
    
    
       
    /*
     * Button Listeners
     */
    
    private final OnClickListener scanListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			//calls activity that generates list of connected devices
			Intent scanRequest = new Intent(VoiceCommActivity.this,ScanList.class);
			startActivityForResult(scanRequest,0);	//0=request code for this activity call

		}
    };
    
    private final OnClickListener callListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {

			//Sends a connection request for the ConnectionListener
	    	sendRequest(targetIP.getText().toString(),"C");
		}
    };
        
            
    private final OnClickListener endListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			//Sends a disconnect request to ConnectionListener
			sendRequest(connectedTo,"D");


		}
    };
    
    private final OnClickListener exitListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			
			//Stopping the service that listens for multicast packets
	        stopService(new Intent(VoiceCommActivity.this,AvailabilityService.class));

			am.setMode(AudioManager.MODE_NORMAL);
			
			
			clThreadStatus = false;
						 
			//ConnectionListner doesn't stop until it receives one more packet after being set to false
			//So, request "E" is sent to itself
			sendRequest("127.0.0.1","E");				
			
				
			if(listenerSocket.isClosed())
				Log.d("VoiceComm", "Listener socket closed");
			
			requestSocket.close();
			if(requestSocket.isClosed())
				Log.d("VoiceComm", "Request socket closed");
			
			
	    	
			finish();
			Log.d("VoiceComm", "Terminated");
	    	
		}
    };
    
    
    /*
     * Called after Activity called with startActivityForResult() finishes 
     * 
     * 			requestCode = 0 -> ScanList activity
     * 						= 1 -> Settings activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode) {
    	case 0:
    		if(resultCode == RESULT_OK) {
    			String target = data.getExtras().getString("selectedIP");
    			targetIP.setText(target);
    		}
    	break;
    	
    	case 1:
    		if(resultCode == RESULT_OK) {
    			ownName = data.getExtras().getString("username");
    			user.setText(ownName);
    		}
    		
    	}
    }
    
    
    /*
     * Handler for communication between ConnectionListener Thread and UI Thread
     */
    public Handler myHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		if(msg.what == 0)		
    			stateChange();
    		
    		if(msg.what == 1)
    			showAlert();
    	}
    };
    
    
    
    public void stateChange() {
    	if(state == CurrentState.CONNECTED) {
    		
    		connStatus.setText("Connected to "+connectedTo);   
    		callButton.setEnabled(false);
    		endButton.setEnabled(true);
    		
    		startVoice(connectedTo.toString());
    		
    	}
    	else if (state == CurrentState.AVAILABLE) {
    		connStatus.setText("Available");
    		callButton.setEnabled(true);
    		endButton.setEnabled(false);
    		
    		stopVoice();
    	}
    }
    
    
    public void showAlert() {
    	incomingBuilder.setMessage("Incoming Call from "+connectedTo);
    	incomingBuilder.show();
    }
    

    
    public void startVoice(String destination) {

        vr = new VoiceReceiver (vport);
    	Log.d("VoiceComm", "VoiceReceiver obj created");
    	receiver = new Thread(vr);
    	receiver.start();
    	Log.d("VoiceComm", "Receiver Thread started");
    	
    	
    	vs = new VoiceSender (destination,vport);
    	Log.d("VoiceComm", "VoiceSender obj created");
    	sender = new Thread(vs);
    	sender.start();
    	Log.d("VoiceComm", "Sender Thread started");
    }
    
    public void stopVoice() {
    	//release the speaker & recorder somehow
    	
    	//Should stop the threads of VS & VR or destory their obj
    	//Thread.stop() or destory() doesn't work now. Modify this.
    	vr.stopRec();
    	vs.stopSend();
    	
    }
    
        
    
    
    /*
     * Listens for packets that initiate/end connection.
     * 			C = Connection Request
     * 			A = Acknowledgement of Connection Request
     * 			D = Disconnect Request
     * 			X = Acknowledgement of Disconnect Request
     * 			R = Rejected	(If user rejects call or is on another call)
     * 			E = Exit request from self
     */
    
    Thread ConnectionListener = new Thread(new Runnable() {

		@Override
		public void run() {
			while(clThreadStatus) {
				
			
			Log.d("CL", "Listening for incoming request");

			try {
				byte[] request = new byte[1];

				String reqMsg;

				listenerSocket = new DatagramSocket(port);

				DatagramPacket requestPacket = new DatagramPacket (request, request.length);

				while(clThreadStatus) {
					

					listenerSocket.receive(requestPacket);
					Log.d("CL", "Request Packet received");

					request = requestPacket.getData();

					reqMsg = new String(requestPacket.getData(),0,requestPacket.getLength());
					Log.d("CL", "Request Packet contains "+reqMsg);


					InetAddress sender = requestPacket.getAddress();

					if (reqMsg.equals("C")) {
						Log.d("CL", "Connection Request Received from " + sender);

						switch(state) {
						case AVAILABLE:
							connectedTo = sender.getHostAddress();

							Log.d("CL", "Now show AlertDialog");
							myHandler.sendEmptyMessage(1);		//msg.what=1 is for showing alert dialog and getting response
							Log.d("CL","Wait for AlertDialog response");
							
							synchronized(lock) {
        						lock.wait();
        					} 

							
							//if receiver accepts. use a global boolean
							if(response == true) {

								byte[] acknowledge = "A".getBytes();
								DatagramPacket ackPacket = new DatagramPacket (acknowledge, acknowledge.length,sender,port);
								listenerSocket.send(ackPacket);
								Log.d("CL", "Ack sent to " + sender);
								state = CurrentState.CONNECTED;
								connectedTo = sender.getHostAddress();
								myHandler.sendEmptyMessage(0);		//msg.what=0 is for invoking stateChange() on main thread through handler

							}
							else {
								byte[] reject = "R".getBytes();
								DatagramPacket rejectPacket = new DatagramPacket (reject, reject.length,sender,port);
								listenerSocket.send(rejectPacket);
								Log.d("CL", "Reject signal sent to " + sender);

							}

							break;

						case CONNECTED:
							byte[] reject = "R".getBytes();
							DatagramPacket rejectPacket = new DatagramPacket (reject, reject.length,sender,port);
							listenerSocket.send(rejectPacket);
							Log.d("CL", "Reject signal sent to " + sender);

							break;
						}

					}

					if (reqMsg.equals("A")) {
						Log.d("CL", "Ack received from " +sender);
						state = CurrentState.CONNECTED;
						connectedTo = sender.getHostAddress();

						//Disable callButton, receiveButton. Enable endButton. Show "Connected" in textview
						myHandler.sendEmptyMessage(0);			

					}

					if (reqMsg.equals("D")) {
						Log.d("CL", "Disconnect signal received from " + sender);

						byte[] disAcknowledge = "X".getBytes();
						DatagramPacket disAckPacket = new DatagramPacket (disAcknowledge, disAcknowledge.length,sender,port);
						listenerSocket.send(disAckPacket);
						Log.d("CL", "Disconnect Ack sent to " + sender);

						state = CurrentState.AVAILABLE;
						connectedTo = null;
						myHandler.sendEmptyMessage(0);
					}

					if (reqMsg.equals("X")) {
						Log.d("CL", "Disconnect ack received from " + sender);

						state = CurrentState.AVAILABLE;
						connectedTo = null;
						myHandler.sendEmptyMessage(0);
					}

					if (reqMsg.equals("R")) {
						Log.d("CL", "Call Rejected by " + sender);
					}
					
					if (reqMsg.equals("E")) {
						Log.d("CL", "Exit Request from " + sender);
					}
					
					
				}

			}catch (SocketException e) {
				Log.e("CL", "SocketException");
				e.printStackTrace();
			} catch (IOException e) {
				Log.e("CL", "IOException");
			} catch (InterruptedException e) {
				Log.e("CL", "InterruptedException");
			}


		}
			
		if(clThreadStatus == false) 
			
			Log.d("CL","Connection Listener thread stopped");
			listenerSocket.close();
			
		}
		
    });
    
    
    
    /*
     * Called when Call or End Button is pressed to forward respective request
     */
    public void sendRequest(String target,String reqType) {

    	
		try {
			requestSocket = new DatagramSocket();

			byte[] request = reqType.getBytes();

			InetAddress targetIP = InetAddress.getByName(target);

			DatagramPacket requestPacket = new DatagramPacket (request,request.length,targetIP,port);

			requestSocket.send(requestPacket);
			Log.d("CR","Request msg "+reqType+" sent to "+target);



		} catch (SocketException e) {
			Log.e("CR","SocketException");
		} catch (IOException e) {
			Log.e("CR", "IOException");
		}
	}
    
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
        
        deviceIP.setText(ownIP);
       } 
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.mainmenu, menu);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	// Launch Settings activity
    	Intent setUsername = new Intent(VoiceCommActivity.this, Settings.class);
    	startActivityForResult(setUsername,1);		//1 = requestCode for this activity
    	return true;
    }
    
}