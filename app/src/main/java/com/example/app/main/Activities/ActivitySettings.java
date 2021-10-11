package com.example.app.main.Activities;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import com.example.app.R;

public class ActivitySettings extends AppCompatActivity {

    String IP_address;
    int IP_port;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().hide();

        //get current IP
        Intent intent = getIntent();
        IP_address = intent.getStringExtra("IP_address");
        IP_port = intent.getIntExtra("IP_port",0);

        EditText ip = (EditText) findViewById(R.id.ip);
        ip.setText(IP_address);

        EditText ip_port = (EditText) findViewById(R.id.ip_port);
        ip_port.setText(String.valueOf(IP_port));

        Button save = findViewById(R.id.save);
        save.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // UPDATE IP address and port
                        IP_address = ip.getText().toString();
                        IP_port = Integer.valueOf(ip_port.getText().toString());

                        intent.putExtra("IP_address", IP_address);
                        intent.putExtra("IP_port", IP_port);

                        setResult(RESULT_OK, intent);
                        finish();
                    }
                });
    }
}