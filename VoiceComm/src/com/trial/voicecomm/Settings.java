package com.trial.voicecomm;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;

public class Settings extends Activity{
	
	private EditText name;
	
	
	SharedPreferences preferences;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    setContentView(R.layout.settings);
	    
	    preferences = PreferenceManager.getDefaultSharedPreferences(this);
	    
	    findViewById(R.id.uname_label);
	    name = (EditText) findViewById(R.id.uname);
	    
	 }
	
	
	public void onBackPressed() {
		Editor edit = preferences.edit();
		edit.putString("username",name.getText().toString());
		edit.commit();
		Intent returnIntent = new Intent();
	    returnIntent.putExtra("username",name.getText().toString());
	    setResult(RESULT_OK,returnIntent);  
		finish();
	}

}
