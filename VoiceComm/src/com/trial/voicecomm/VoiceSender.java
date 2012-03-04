package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class VoiceSender extends Thread {
	
	public DatagramSocket socket;
	private int port;			//which port??
	public AudioRecord recorder;
	String target = null;
	
	//Audio Configuration. 
	private int sampleRate = 16000;		//How much will be ideal?
	private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;    //Mono Makes sense
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;		//PCM 16BIT is compatible with most
	
	private boolean status = true;
	
	
	public VoiceSender(String target,int port) {
		this.target = target;
		this.port = port;
		
	}
	
	@Override
	public void run() {
		try {
			
		
			//Is setting thread priority needed? If yes then to what?
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			
			DatagramSocket socket = new DatagramSocket();
			Log.d("VS", "Socket Created");
			
			Log.d("VS", "Retrieving address of "+target);
			InetAddress destination = InetAddress.getByName(target);
			Log.d("VS", "Address retrieved");
			
			
			DatagramPacket packet;
			
			//minimum buffer size. need to be careful. might cause problems. try setting manually if any problems faced
			//int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
			int minBufSize = 256;
			
			byte[] buffer = new byte[minBufSize];
			Log.d("Vs", "Buffer created of size "+minBufSize);
			
			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,minBufSize*20);
			Log.d("VS", "Recorder initialized");
			
			recorder.startRecording();
			Log.d("VS", "Recording Started");
			
			
			while(status == true) {
				
				
				//reading data from MIC into buffer
				recorder.read(buffer, 0, buffer.length);			//3rd parameter = no. of requested bytes = buffer.length or minbufsize
				Log.d("VS", "Audio read into buffer");
				
				//putting buffer in the packet
				packet = new DatagramPacket (buffer,buffer.length,destination,port);
				Log.d("VS", "Packet created");
			
				socket.send(packet);
				Log.d("VS", "Packet sent");
				
				
			}
			
			
			
		} catch(UnknownHostException e) {
			Log.e("VS", "UnknownHostException");
		} catch (IOException e) {
			Log.e("VS", "IOException");
		} 
		
		
	}
	
	
}
		
	

