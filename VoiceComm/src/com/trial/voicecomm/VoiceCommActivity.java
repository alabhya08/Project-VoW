
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
import android.widget.Toast;



public class VoiceCommActivity extends Activity {
    
	//AppConfig config;
	
	AppState state;
	
	public int requestSendPort = 40005;

    public int voicePort = 50005;
    
    public int requestListenPort = 30005;
	
	public String ownIP, ownName;
		
	private DatagramSocket requestSocket;
	
	private DatagramPacket replyPacket,requestPacket;

	public byte[] replyByte,requestByte ;
	
	public String reply;
	
	public String targetName;			//targetName =  name returned from scanlist
	
	public volatile String request, requester, requesterName;		//requester = ip string returned from RequestService. requesterName = same's name
	
	public InetAddress requesterIP;							

	public boolean response;			//Receiver's response to incoming call. true = accept. false = reject

	private AudioManager am;

		

		
	WifiManager wm;
    
    WifiManager.MulticastLock multicastLock;
    
    //private NotificationManager nm;

	//UI Elements
	private EditText targetIP;
	private Button callButton,endButton,scanButton;
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
        
        state = ((AppState)getApplicationContext());
        
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
        
                
        
        findViewById(R.id.user_label);
        user = (TextView) findViewById(R.id.user);
        deviceIP = (TextView) findViewById(R.id.device_IP);
        targetIP = (EditText) findViewById(R.id.target_ip);
        scanButton = (Button) findViewById(R.id.scan_button);
        callButton = (Button) findViewById(R.id.call_button);
        endButton = (Button) findViewById(R.id.end_button);
        
        connStatus = (TextView) findViewById (R.id.conn_status);
                
        scanButton.setOnClickListener(scanListener);
        callButton.setOnClickListener(callListener);
        endButton.setOnClickListener(endListener);
        
        
        
        
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
        //am.setMode(AudioManager.MODE_IN_CALL);
        am.setSpeakerphoneOn(false);
    	
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	unregisterReceiver(requestBroadcastReceiver);
    	Log.d("VCA","requestBroacastReceiver unregistered");
    	
    	
    	
    	//am.setMode(AudioManager.MODE_NORMAL);
    	
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
    		requesterName = extras.getString("requesterName");
    	}
    	
    	//This is not getting the correct intent
    	Log.d("VCA", "onNewIntent() : Received intent with request "+request+" from "+requester);
    	
    	checkRequestType();
    	
    	
    }
    
       
   
    
    /*
     * Called after Activity called with startActivityForResult() finishes 
     * 
     * 			requestCode = 0 -> ScanList activity
     * 						= 1 -> Settings activity
     * 						= 2 -> IncomingCall activity
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch(requestCode) {
    	case 0:	//ScanList
    		if(resultCode == RESULT_OK) {
    			requesterName = data.getExtras().getString("selectedName");
    			    			    			
    			targetIP.setText(data.getExtras().getString("selectedIP"));			
    		}
    		break;
    	
    	case 1: //Settings
    		if(resultCode == RESULT_OK) {
    			ownName = data.getExtras().getString("username");
    			user.setText(ownName);
    		}
    		break;
    	
    	case 2: //IncomingCallActivity
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
				reply = "A";
				replyByte = reply.getBytes();
				replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
				requestSocket.send(replyPacket);
				Log.d("CRT#", "Ack sent to " + requesterIP);
				state.setBusy();
				Log.d("VCA", ownName + " Busy");
				stateChange(requesterIP.getHostAddress(), requesterName);
			}

			else {
				reply = "R";
				replyByte = reply.getBytes();
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
        	//requesterName = intent.getStringExtra("requesterName");
        	Log.d("VCA", "Broadcast Rec : Received intent with request "+request+" from "+requester);
        	
        	checkRequestType();
        }
    };
    
    
    
    public void stateChange(String inCallWithIP, String inCallWithName) {
    	if( !state.isAvailable()) {
    		
    		connStatus.setText("Connected to "+inCallWithName+"("+inCallWithIP+")");   
    		callButton.setEnabled(false);
    		endButton.setEnabled(true);
    		
    		startVoice(inCallWithIP.toString());
    		
    	}
    	else if ( state.isAvailable()) {
    		connStatus.setText("Available");
    		callButton.setEnabled(true);
    		endButton.setEnabled(false);
    		
    		stopVoice();
    	}
    }
    
    
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
     * 			R = Rejected
     * 			B = User Busy
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

						if( state.isAvailable()) {
						
														
							Log.d("CRT", "Start IncomingCall Activity to get response");
							Intent getCallResponse = new Intent(VoiceCommActivity.this, IncomingCallActivity.class);
							getCallResponse.putExtra("caller", requesterName);
					    	startActivityForResult(getCallResponse,2);		//2 = requestCode for this
														
						}

						else {
							reply = "R";
							replyByte = reply.getBytes();
							replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
							requestSocket.send(replyPacket);
							Log.d("CRT", "Reject signal sent to " + requesterIP);

							
						}

					}

					if (request.equals("A")) {
						Log.d("CRT", "Ack received from " +requesterIP);
						state.setBusy();
						Log.d("VCA", ownName + " Busy");
																	
						stateChange(requesterIP.getHostAddress(),requesterName);			

					}

					if (request.equals("D")) {
						Log.d("CRT", "Disconnect signal received from " + requesterIP);
						
						/*
						reply = "X";
						replyByte = reply.getBytes();
						
						replyPacket = new DatagramPacket (replyByte, replyByte.length,requesterIP,requestSendPort);
						requestSocket.send(replyPacket);
						Log.d("CRT", "Disconnect Ack sent to " + requesterIP);
						*/
						state.setAvailable();
						Log.d("VCA", ownName + " Available");
						
						stateChange(null,null);
					}
					
					/*
					if (request.equals("X")) {
						Log.d("CRT", "Disconnect ack received from " + requesterIP);
						state.setAvailable();
						Log.d("VCA", ownName + " Available");
						stateChange(null,null);
						
					}
					*/

					if (request.equals("R")) {
						Log.d("CRT", "Call Rejected by " + requesterIP);
						Toast.makeText(this, "Call Rejected by" + requesterName, Toast.LENGTH_SHORT).show();
						
					}
					
					if (request.equals("B")) {
						Log.d("CRT",requesterIP +" is Busy");
						Toast.makeText(this, requesterName +" is on another call", Toast.LENGTH_SHORT).show();
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
									
			//Condition added to handle case when call is pressed without any input
			if(target.equals("")) {
				Toast.makeText(this, "Please enter an address or scan for devices", Toast.LENGTH_SHORT).show();
				Log.d("CRT$","target address blank");
			}
			
			else {
				
				
				
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
			sendRequest(requester,"D");
			state.setAvailable();
			Log.d("VCA", ownName + " Available");
			
			stateChange(null,null);


		}
    };
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.mainmenu, menu);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case R.id.settings:
    		// Launch Settings activity
        	Intent setUsername = new Intent(VoiceCommActivity.this, Settings.class);
        	startActivityForResult(setUsername,1);		//1 = requestCode for tthis
        	return true;
        	
    	case R.id.exit:
    		//Stopping the service that listens for multicast packets
	        stopService(new Intent(VoiceCommActivity.this,AvailabilityService.class));
	        stopService(new Intent(VoiceCommActivity.this,RequestService.class));

	        //Stopping Audio transfer, just in case app is exited while in call
	        if(!state.isAvailable()) {
	        	if(vr.isRunning())
	        		vr.stopRec();
	        	if(vs.isRunning())
	        		vs.stopSend();
	        }
	        
			am.setMode(AudioManager.MODE_NORMAL);
						
			finish();
			Log.d("VCA", "Terminated");
			return true;
			
    	default:
            return super.onOptionsItemSelected(item);
    		
    	}
		    	
    	
    }
    
}