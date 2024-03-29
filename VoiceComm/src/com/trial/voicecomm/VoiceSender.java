//Captures voice from mic and sends it to target

package com.trial.voicecomm;

import static com.trial.voicecomm.Configuration.audioFormat;
import static com.trial.voicecomm.Configuration.channelConfig;
import static com.trial.voicecomm.Configuration.minBufSize;
import static com.trial.voicecomm.Configuration.sampleRate;
import static com.trial.voicecomm.Configuration.speaker_recorder_bufSize;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class VoiceSender extends Thread {

	public DatagramSocket socket;
	private int port;			
	public AudioRecord recorder;
	String target = null;

	//Audio Configuration. 
	
	private volatile boolean sending = true;


	public VoiceSender(String target,int port) {
		this.target = target;
		this.port = port;

	}
	
	public void stopSend() {
		sending = false;
		
	}
	
	boolean isRunning() {
		if(!sending)
			return false;
		else
			return true;
	}

	
	@Override
	public void run() {
		while(sending == true) {
		
		try {


			//Is setting thread priority needed? If yes then to what?
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

			socket = new DatagramSocket();
			Log.d("VS", "Socket Created");

			Log.d("VS", "Retrieving address of "+target);
			InetAddress destination = InetAddress.getByName(target);
			Log.d("VS", "Address retrieved");


			DatagramPacket packet;

			//minimum buffer size. need to be careful. might cause problems. try setting manually if any problems faced
			//int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
			

			byte[] buffer = new byte[minBufSize];
			Log.d("VS Status", "Buffer created of size "+minBufSize);

			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,speaker_recorder_bufSize);
			Log.d("VS Status", "Recorder initialized");

			recorder.startRecording();
			Log.d("VS Status", "Recording Started");


			while(sending) {


				//reading data from MIC into buffer
				recorder.read(buffer, 0, buffer.length);			//3rd parameter = no. of requested bytes = buffer.length or minbufsize
				//Log.d("VS", "Audio read into buffer");

				//putting buffer in the packet
				packet = new DatagramPacket (buffer,buffer.length,destination,port);
				//Log.d("VS", "Packet created");

				socket.send(packet);
				//Log.d("VS", "Packet sent");


			}



		} catch(UnknownHostException e) {
			Log.e("VS", "UnknownHostException");
		} catch (IOException e) {
			Log.e("VS", "IOException");
		} 

		}
		if(sending == false) {
			Log.d("VS Status","Sending Stopped!");
			recorder.stop();
			Log.d("VS Status","Recorder stopped");
			recorder.release();
			Log.d("VS Status","Recorder Released");
			socket.close();
			Log.d("VS Status","Socket Closed");
		}
	}
	
	
	

}

