package com.shout.android;

import android.app.AlertDialog;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.view.Menu;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.shout.android.core.BluetoothClient;
import com.shout.android.core.ConnectionListener;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements ConnectionListener {

    private final BluetoothClient bluetoothClient =
            BluetoothClient.getInstance();
    private TextView numPeopleShouting;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(BluetoothClient.getInstance().getForegroundBackgroundListener());
        BluetoothClient.getInstance().registerConnectionListener(this);
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        myToolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.setUsername) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Set Username");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
                builder.setView(input);

                builder.setPositiveButton("OK", (dialog, which) -> BluetoothClient.getInstance().setUsername(input.getText().toString()));
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            }
            return false;
        });
        bluetoothClient.initialize(this,MainActivity.this);
        EditText editText = findViewById(R.id.editText);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                ChatMessage cm = new ChatMessage(v.getText().toString(), BluetoothClient.getInstance().getUsername(), new Date().getTime(), BluetoothClient.getInstance().getUserID());
                bluetoothClient.sendMessage(cm);
                v.setText("");
                return true;
            }
            return false;
        });
        numPeopleShouting = findViewById(R.id.numPeopleShouting);
        initUsername();
    }

    private void initUsername() {

        Intent intent = getIntent();
        String message = intent.getStringExtra(LoginActivity.USERNAME_ID_STRING);
        if (message != null) {
            BluetoothClient.getInstance().setUsername(message);
            BluetoothClient.getInstance().connect();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Username");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            BluetoothClient.getInstance().setUsername(input.getText().toString());
            BluetoothClient.getInstance().connect();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            initUsername();
        });

        builder.show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Start Bridgefy
            bluetoothClient.startScanning(MainActivity.this);

        } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Location permissions needed to start peers discovery.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void deviceConnected(String username, long timestamp, String userID) {

    }

    @Override
    public void deviceLost(String username, long timestamp, String userID) {

    }

    @Override
    public void connectedDeviceCountChanged(int count) {
        if (count == 1) {
            numPeopleShouting.setText(getString(R.string.person_shouting_template));
        } else {
            numPeopleShouting.setText(getString(R.string.people_shouting_template, count));
        }
    }
}
