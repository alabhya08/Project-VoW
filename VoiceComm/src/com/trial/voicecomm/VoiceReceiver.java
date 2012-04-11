package com.trial.voicecomm;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class VoiceReceiver extends Thread {
	public DatagramSocket socket;
	public AudioTrack speaker;


	private int port;			//which port??

	//Audio Configuration. 
	private int sampleRate = 8000;		//How much will be ideal?
	private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;    //Mono Makes sense
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;		//PCM 16BIT is compatible with most

	private volatile boolean receiving = true;


	public VoiceReceiver(int port) {
		this.port = port;

	}
	
	public void stopRec() {
		receiving = false;
	
	}
	
	boolean isRunning() {
		if(!receiving)
			return false;
		else
			return true;
	}
	


	@Override
	public void run() {
		
		while(receiving == true) {

		try {

			//Is setting thread priority needed? If yes then to what?
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

			DatagramSocket socket = new DatagramSocket(port);
			Log.d("VR Status", "Socket Created");

			//minimum buffer size. need to be careful. might cause problems. try setting manually if any problems faced
			//int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
			int minBufSize = 256;
			byte[] buffer = new byte[minBufSize];
			Log.d("VR Status", "Buffer Created of size "+minBufSize);

			speaker = new AudioTrack(AudioManager.STREAM_MUSIC,sampleRate,channelConfig,audioFormat,minBufSize*20,AudioTrack.MODE_STREAM);
			Log.d("VR Status", "AudioTrack obj created");

			speaker.play();
			Log.d("VR Status","Speaker started playing");
			

			while(receiving) {
				try {
					DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
					socket.receive(packet);
					Log.d("VR", "Packet Received");

					//check if it is a audio packet and only write to speaker if yes
					//OR check if its NOT initiating/ending packet or is not blank


					//reading content from packet
					buffer=packet.getData();
					Log.d("VR", "Packet data read into buffer");

					//sending data to the Audiotrack obj i.e. speaker
					speaker.write(buffer, 0, buffer.length);
					Log.d("VR", "Writing buffer content to speaker");

				} catch(IOException e) {
					Log.e("VR","IOException");
				}
			}


		} catch (SocketException e) {
			Log.e("VR", "SocketException");
		}
		
		}
		if(receiving == false) {
			Log.d("VR Status","Receiving Stopped!");
			speaker.stop();
			Log.d("VR Status","Speaker Stopped Playing");
			speaker.release();
			Log.d("VR Status","Speaker Released");
			//socket.close();
			//Log.d("VR Status","Socket Closed");
		}

	}



}