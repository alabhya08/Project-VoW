

package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;



public class VoiceCommActivity extends Activity {
    

	
	public int port = 40005;			//which port??
	
	public int vport = 50005;
	
	private AudioManager am;
	
	public static CurrentState state;
	
	public String connectedTo;
	
	public boolean response;			//Receiver's response to incoming call. true = accept. false = reject
	
	public Object lock;					//for synchronized block used for wait/notify
	
	//UI Elements
	private EditText targetIP;
	private Button callButton,endButton,exitButton;
	private TextView connStatus;
	
	AlertDialog.Builder incomingBuilder; 
	
	VoiceSender vs;
	VoiceReceiver vr;
		
	Thread sender,receiver;
	
	
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        targetIP = (EditText) findViewById(R.id.target_ip);
        callButton = (Button) findViewById(R.id.call_button);
        endButton = (Button) findViewById(R.id.end_button);
        exitButton = (Button) findViewById(R.id.exit_button);
        connStatus = (TextView) findViewById (R.id.conn_status);
                
        callButton.setOnClickListener(callListener);
        endButton.setOnClickListener(endListener);
        exitButton.setOnClickListener(exitListener);
        
        state = CurrentState.AVAILABLE;
        
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
                
        ConnectionListener.start();
        
      
        
        
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        
       
        
    }
       
    
    
    private final OnClickListener callListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			
			am.setMode(AudioManager.MODE_IN_CALL);
	        am.setSpeakerphoneOn(false);
	        
			//Sends a connection request for the ConnectionListener
	    	sendRequest(targetIP.getText().toString(),"C");
		}
    };
        
            
    private final OnClickListener endListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			
			am.setMode(AudioManager.MODE_NORMAL);
			
			//Sends a disconnect request to ConnectionListener
			sendRequest(connectedTo,"D");
			
			
		}
    };
    
    private final OnClickListener exitListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			
			am.setMode(AudioManager.MODE_NORMAL);
	    	Log.d("VoiceComm", "Terminated");
	    	System.exit(0);
		}
    };
    
    
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
    	//incomingBuilder.setMessage("Incoming Call from "+connectedTo);
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
    	sender.stop();
    	receiver.stop();
    }
    
        
    
    
    /*
     * Listens for packets that initiate/end connection.
     * 			C = Connection Request
     * 			A = Acknowledgement of Connection Request
     * 			D = Disconnect Request
     * 			X = Acknowledgement of Disconnect Request
     * 			R = Rejected	(If user rejects call or is on another call)
     */
    
    Thread ConnectionListener = new Thread(new Runnable() {

		@Override
		public void run() {
			Log.d("CL", "Listening for incoming request");
			
			try {
				byte[] request = new byte[1];
				
				String reqMsg;
				
				DatagramSocket socket = new DatagramSocket(port);
				
			
			
				while(true) {
					DatagramPacket requestPacket = new DatagramPacket (request, request.length);
					
					socket.receive(requestPacket);
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
							
							Log.d("CL","Response received");
							//if receiver accepts. use a global boolean
							if(response == true) {
								
								byte[] acknowledge = "A".getBytes();
								DatagramPacket ackPacket = new DatagramPacket (acknowledge, acknowledge.length,sender,port);
								socket.send(ackPacket);
								Log.d("CL", "Ack sent to " + sender);
								state = CurrentState.CONNECTED;
								connectedTo = sender.getHostAddress();
								myHandler.sendEmptyMessage(0);		//msg.what=0 is for invoking stateChange() on main thread through handler
								
							}
							else {
								byte[] reject = "R".getBytes();
								DatagramPacket rejectPacket = new DatagramPacket (reject, reject.length,sender,port);
								socket.send(rejectPacket);
								Log.d("CL", "Reject signal sent to " + sender);
								
							}
							
							break;
							
						case CONNECTED:
							byte[] reject = "R".getBytes();
							DatagramPacket rejectPacket = new DatagramPacket (reject, reject.length,sender,port);
							socket.send(rejectPacket);
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
						socket.send(disAckPacket);
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
				}
			
			}catch (SocketException e) {
				Log.e("CL", "SocketException");
			} catch (IOException e) {
				Log.e("CL", "IOException");
			} catch (InterruptedException e) {
				Log.e("CL", "InterruptedException");
			}
			
			
		}
    	
    });
    
    
    
    /*
     * Called when Call or End Button is pressed to forward respective request
     */
    public void sendRequest(String target,String reqType) {
		
    	
		try {
			DatagramSocket socket = new DatagramSocket();
			
			byte[] request = reqType.getBytes();
			
			InetAddress targetIP = InetAddress.getByName(target);
			
			DatagramPacket requestPacket = new DatagramPacket (request,request.length,targetIP,port);
			
			socket.send(requestPacket);
			Log.d("CR","Request msg "+reqType+" sent to "+target);
			
			
			
		} catch (SocketException e) {
			Log.e("CR","SocketException");
		} catch (IOException e) {
			Log.e("CR", "IOException");
		}
	}
    
    
}