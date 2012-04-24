//Shows the incoming call screen

package com.trial.voicecomm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class IncomingCallActivity extends Activity {
	
	private TextView callerName;
	private Button acceptButton, rejectButton;
	private boolean myResponse;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.call);
		
		findViewById(R.id.incoming_label);
		callerName = (TextView) findViewById(R.id.caller_name);
		acceptButton = (Button) findViewById(R.id.accept_button);
		rejectButton = (Button) findViewById(R.id.reject_button);
		
		acceptButton.setOnClickListener(acceptListener);
		rejectButton.setOnClickListener(rejectListener);
		
		Bundle extra = getIntent().getExtras();
		callerName.setText(extra.getString("caller"));
		
	}
	
	private final OnClickListener acceptListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			myResponse = true;
			Intent responseIntent = new Intent();
		    responseIntent.putExtra("response",myResponse);
		    setResult(RESULT_OK,responseIntent);  
		    Log.d("ICA","Sending result with response "+myResponse);
	    	finish();
			
		}
    };
    
    private final OnClickListener rejectListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			myResponse = false;
			Intent responseIntent = new Intent();
		    responseIntent.putExtra("response",myResponse);
		    setResult(RESULT_OK,responseIntent);        
		    Log.d("ICA","Sending result with response "+myResponse);
	    	finish();
			
		}
    };
    
    public void onBackPressed() {
    	Intent responseIntent = new Intent();
	    responseIntent.putExtra("response",false);
	    setResult(RESULT_OK,responseIntent);        
    	finish();
    }


}
