
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
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
    

	public int requestSendPort = 40005;

    public int voicePort = 50005;
    
    public int requestListenPort = 30005;
	
	public String ownIP, ownName;
		
	private DatagramSocket requestSocket;
	
	private DatagramPacket replyPacket,requestPacket;

	public byte[] replyByte,requestByte ;
	
	public String connectedTo, target, targetName;
	
	public String request, requester, requesterName;
	
	public InetAddress requesterIP;

	public boolean response;			//Receiver's response to incoming call. true = accept. false = reject

	private AudioManager am;

	public static CurrentState state;

		
	WifiManager wm;
    
    WifiManager.MulticastLock multicastLock;
    
    //private NotificationManager nm;

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
        
        //nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        
        wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("myLock");
        
        multicastLock.acquire();			//lock automatically released when app exits/crashes
        Log.d("VCA","multicast lock acquired");
        
        
        
        //Starting the service that listens for multicast packets
        startService(new Intent(VoiceCommActivity.this,AvailabilityService.class));
        Log.d("VCA","Availability Service started");
        
        //Starting the service that listens for connection request packets
        startService(new Intent(VoiceCommActivity.this,RequestService.class));
        Log.d("VCA","Request Service started");
        
        //registerReceiver(requestBroadcastReceiver, new IntentFilter(RequestService.FORWARD_REQUEST));
        //Log.d("VCA","requestBroacastReceiver registered");
        
        
       
        
        
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
         
        
        
        /*
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
        */
        
        
        
        
        
               
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	registerReceiver(requestBroadcastReceiver, new IntentFilter(RequestService.FORWARD_REQUEST));
    	Log.d("VCA","requestBroacastReceiver registered");
    	
    	
    	
    	am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setMode(AudioManager.MODE_IN_CALL);
        am.setSpeakerphoneOn(false);
    	
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	unregisterReceiver(requestBroadcastReceiver);
    	Log.d("VCA","requestBroacastReceiver unregistered");
    	
    	
    	
    	am.setMode(AudioManager.MODE_NORMAL);
    	
    }
    
    @Override
    public void onBackPressed() {
       Intent setIntent = new Intent(Intent.ACTION_MAIN);
       setIntent.addCategory(Intent.CATEGORY_HOME);
       setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       startActivity(setIntent);
    }
    
    
    
    public void onNewIntent(Intent intent) {
    	Bundle extras = intent.getExtras();
    	
    	if(extras != null) {
    		request = extras.getString("request");
    		requester = extras.getString("requester");
    		targetName = extras.getString("requesterName");
    	}
    	
    	Log.d("VCA", "Received intent with request "+request+" from "+requester);
    	
    	checkRequestType();
    	
    	
    }
    
       
    /*
     * Button Listeners
     */
    
    private final OnClickListener scanListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			//calls activity that generates list of connected devices
			Intent scanRequest = new Intent(VoiceCommActivity.this,ScanList.class);
			startActivityForResult(scanRequest,0);	//0=request code for this activity call.

		}
    };
    
    private final OnClickListener callListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {

			//Sends a connection request for the ConnectionListener
	    	sendRequest(targetIP.getText().toString(),"C"+ownName);
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
	        stopService(new Intent(VoiceCommActivity.this,RequestService.class));

	        //Stopping Audio transfer, just in case app is exited while in call
	        if(state == CurrentState.CONNECTED) {
	        	if(vr.isRunning())
	        		vr.stopRec();
	        	if(vs.isRunning())
	        		vs.stopSend();
	        }
	        
			am.setMode(AudioManager.MODE_NORMAL);
						
			finish();
			Log.d("VCA", "Terminated");
	    	
		}
    };
    
    
    /*
     * Called after Activity called with startActivityForResult() finishes 
     * 
     * 			requestCode = 0 -> ScanList activity
     * 						= 1 -> Settings activity
     * 						= 2 -> IncomingCall activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode) {
    	case 0:
    		if(resultCode == RESULT_OK) {
    			targetName = data.getExtras().getString("selectedName");
    			
    			target = data.getExtras().getString("selectedIP");
    			targetIP.setText(target);
    		}
    		break;
    	
    	case 1:
    		if(resultCode == RESULT_OK) {
    			ownName = data.getExtras().getString("username");
    			user.setText(ownName);
    		}
    		break;
    	
    	case 2:
    		if(resultCode == RESULT_OK) {
    			response = data.getExtras().getBoolean("response");
    			Log.d("VCA","IncomingCallActivity result : "+response);
    			
    			onICAResponse();
    		}
    		break;
    		
    	}
    }
    
    public void onICAResponse() {
    	/*This part had to be moved here from the packetChecker() as startActivityForResult()
		 * is non-blocking.
		 */
		try {
			requestSocket = new DatagramSocket(requestListenPort);
			Log.d("CRT","RequestSocket created");
					
			if(response == true) {

				replyByte = "A".getBytes();
				replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
				requestSocket.send(replyPacket);
				Log.d("CRT#", "Ack sent to " + requesterIP);
				state = CurrentState.CONNECTED;
				connectedTo = requesterIP.getHostAddress();
				stateChange();
			}

			else {
				replyByte = "R".getBytes();
				replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
				requestSocket.send(replyPacket);
				Log.d("CRT#", "Reject signal sent to " + requesterIP);

			}
		
			if(!requestSocket.isClosed()) {
				requestSocket.close();
				Log.d("CRT$","RequestSocket closed");
			}
		
		}catch(IOException e) {
			Log.e("CRT#","IO Exception");
		}

    }
    
        
    private BroadcastReceiver requestBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	request = intent.getStringExtra("request");
        	requester = intent.getStringExtra("requester");
        	Log.d("VCA", "Received intent with request "+request+" from "+requester);
        	
        	checkRequestType();
        }
    };
    
    
    
    public void stateChange() {
    	if(state == CurrentState.CONNECTED) {
    		
    		connStatus.setText("Connected to "+targetName+"("+connectedTo+")");   
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
    
    /*
    public void showNotification() {
    	//System wide notification
    	Notification notification = new Notification(R.drawable.ic_launcher, "Incoming Call", System.currentTimeMillis());
    	
    	notification.flags = Notification.FLAG_AUTO_CANCEL;
    	CharSequence contentTitle = "Incoming Call";
    	CharSequence contentText = connectedTo;
    	Intent notificationIntent = new Intent(this, IncomingCallActivity.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    	notification.setLatestEventInfo(getApplicationContext(), contentTitle, contentText, contentIntent);
    	
    	nm.notify(1, notification);
    }
    */
    
    /*public void showAlert() {
    	    	
    	//Alert Dialog in App
    	incomingBuilder.setMessage("Incoming Call from "+connectedTo);
    	incomingBuilder.show();
    }
    */

    
    public void startVoice(String destination) {

        vr = new VoiceReceiver (voicePort);
    	Log.d("VCA", "VoiceReceiver obj created");
    	receiver = new Thread(vr);
    	receiver.start();
    	Log.d("VCA", "Receiver Thread started");
    	
    	
    	vs = new VoiceSender (destination,voicePort);
    	Log.d("VCA", "VoiceSender obj created");
    	sender = new Thread(vs);
    	sender.start();
    	Log.d("VCA", "Sender Thread started");
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
     */
    
    
    public void checkRequestType() {
    	
		Log.d("CRT", "Checking request type of packet received in RS");

			try {
				requestSocket = new DatagramSocket(requestListenPort);
				Log.d("CRT","RequestSocket created");
				
				requesterIP = InetAddress.getByName(requester);
				Log.d("CRT","requesterIP retrieved: "+requesterIP);

					if (request.equals("C")) {
						Log.d("CRT", "Connection Request Received from " + requesterIP);

						switch(state) {
						case AVAILABLE:
														
							Log.d("CRT", "Start IncomingCall Activity to get response");
							Intent getCallResponse = new Intent(VoiceCommActivity.this, IncomingCallActivity.class);
							getCallResponse.putExtra("caller", connectedTo);	//connectedTo.getHostAddress()???
					    	startActivityForResult(getCallResponse,2);		//2 = requestCode for this
														
							
							break;

						case CONNECTED:
							replyByte = "R".getBytes();
							replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
							requestSocket.send(replyPacket);
							Log.d("CRT", "Reject signal sent to " + requesterIP);

							break;
						}

					}

					if (request.equals("A")) {
						Log.d("CRT", "Ack received from " +requesterIP);
						state = CurrentState.CONNECTED;
						connectedTo = requesterIP.getHostAddress();
						
						stateChange();			

					}

					if (request.equals("D")) {
						Log.d("CRT", "Disconnect signal received from " + requesterIP);

						replyByte = "X".getBytes();
						replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
						requestSocket.send(replyPacket);
						Log.d("CRT", "Disconnect Ack sent to " + requesterIP);

						state = CurrentState.AVAILABLE;
						connectedTo = null;
						stateChange();
					}

					if (request.equals("X")) {
						Log.d("CRT", "Disconnect ack received from " + requesterIP);

						state = CurrentState.AVAILABLE;
						connectedTo = null;
						stateChange();
					}

					if (request.equals("R")) {
						Log.d("CRT", "Call Rejected by " + requesterIP);
						//Show call rejected to caller
					}
							
					
					if(!requestSocket.isClosed()) {
						requestSocket.close();
						Log.d("CRT","RequestSocket closed");
					}
					
				
			}catch (SocketException e) {
				Log.e("CRT", "SocketException");
				e.printStackTrace();
			} catch (IOException e) {
				Log.e("CRT", "IOException");
			}


		}
			
		
    
    
    
    /*
     * Called when Call or End Button is pressed to forward respective request
     */
    public void sendRequest(String target,String reqType) {

    	
		try {
			requestSocket = new DatagramSocket(requestListenPort);
			Log.d("CRT$","RequestSocket created");
			
			requestByte = reqType.getBytes();

			InetAddress targetIP = InetAddress.getByName(target);

			requestPacket = new DatagramPacket (requestByte,requestByte.length,targetIP,requestSendPort);

			requestSocket.send(requestPacket);
			Log.d("CRT$","Request msg "+reqType+" sent to "+target);

			if(!requestSocket.isClosed()) {
				requestSocket.close();
				Log.d("CRT$","RequestSocket closed");
			}

		} catch (SocketException e) {
			Log.e("CRT$","SocketException");
		} catch (IOException e) {
			Log.e("CRT$", "IOException");
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
            Log.e("VCA", "SocketException in setOwnIP()");
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
    	startActivityForResult(setUsername,1);		//1 = requestCode for tthis
    	return true;
    }
    
}