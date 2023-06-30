package com.example.picamera;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderListener;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity implements HBRecorderListener {

    private final int SCREEN_RECORD_REQUEST_CODE = 313;
    private final File VIDEOS = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);

    private WebView webView;
    private ProgressBar connectionProgressBar;
    private HBRecorder recorder;
    private ImageView connectImageView;
    private ImageView locationImageView;
    private ImageView recordImageView;
    private TextView recordTextView;

    private boolean recordStatus;
    private AudioRecord audioRecorder;
    private int audioPort = 8765;

    private static final String Shared_KEY = "com.example.picamera";
    private SharedPreferences sharedPreferences;

    private boolean isConnected = false;
    private boolean isRecording = false;
    private boolean isLocating = false;

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        int flagFullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(flagFullscreen, flagFullscreen);
        setContentView(R.layout.activity_main);

        askPermissions();

        sharedPreferences = getSharedPreferences(Shared_KEY, Context.MODE_PRIVATE);

        recorder = new HBRecorder(this, this);
        recorder.isAudioEnabled(false);

        webView = findViewById(R.id.webView);
        connectionProgressBar = findViewById(R.id.connectionProgressBar);
        connectImageView = findViewById(R.id.connectImageView);
        locationImageView = findViewById(R.id.locationImageView);
        recordImageView = findViewById(R.id.recordImageView);
        recordTextView = findViewById(R.id.recordTextView);
        ImageView settingsImageView = findViewById(R.id.settingsImageView);

        recordTextView.setVisibility(View.GONE);
        connectionProgressBar.setVisibility(View.GONE);
        connectImageView.setOnClickListener(v -> connectImageViewOnClickAction());
        recordImageView.setOnClickListener(v -> recordImageViewOnClickAction());
        locationImageView.setOnClickListener(v -> locationImageViewOnClickAction());
        settingsImageView.setOnClickListener(v -> settingsImageViewOnClickAction());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) recorder.pauseScreenRecording();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) recorder.pauseScreenRecording();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRecording) recorder.resumeScreenRecording();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) recorder.stopScreenRecording();
    }

    @Override
    public void HBRecorderOnStart() {
        Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
        recordTextView.setVisibility(View.VISIBLE);
        int time = sharedPreferences.getInt("AutoSave", -1);
        if (time > 0)
            new Thread(() -> {
                try {
                    Thread.sleep(60L * 1000 * time);
                    runOnUiThread(() -> {
                        recorder.stopScreenRecording();
                        recordImageView.setBackgroundResource(R.drawable.ic_record);
                        isRecording = false;
                    });
                } catch (Exception ignored) {
                }
            }).start();
    }

    @Override
    public void HBRecorderOnComplete() {
        Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show();
        recordTextView.setVisibility(View.GONE);
    }

    @Override
    public void HBRecorderOnError(int errorCode, String reason) {
        Toast.makeText(this, "Error: " + reason, Toast.LENGTH_LONG).show();
    }

    private void startRecordingScreen() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = mediaProjectionManager != null ? mediaProjectionManager.createScreenCaptureIntent() : null;
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                recordImageView.setBackgroundResource(R.drawable.ic_stop);
                recorder.startScreenRecording(data, resultCode);
                isRecording = true;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void askPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView configWebView(WebView view) {
        view.setWebViewClient(new MyWebViewClient());
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        return view;
    }

    private void locationImageViewOnClickAction() {
        if (isLocating) {
            isLocating = false;
            locationImageView.setBackgroundResource(R.drawable.ic_location);
            String savedUrl = sharedPreferences.getString("URL", "");
            connectionProgressBar.setVisibility(View.VISIBLE);
            webView.setWebViewClient(new MyWebViewClient());
            webView.loadUrl(savedUrl + ":8000");
            return;
        }
        isLocating = true;
        locationImageView.setBackgroundResource(R.drawable.ic_no_location);
        connectionProgressBar.setVisibility(View.VISIBLE);
        webView = configWebView(webView);
        String savedUrl = sharedPreferences.getString("URL", "");
        webView.loadUrl(savedUrl + ":8000/current_location");
//        webView.loadUrl("https://maps.google.com/maps?q=(35.7,51.43)");
    }


    private void connectImageViewOnClickAction() {
        if (isConnected) {
            isConnected = false;
            connectImageView.setBackgroundResource(R.drawable.ic_connect);
            webView.loadUrl("");
            recordStatus = false;
            try {
                audioRecorder.release();
            } catch (Exception ignored) {}
            Log.d("VS","Recorder released");
            return;
        }
        final View view = getLayoutInflater().inflate(R.layout.dialog_connection, null);
        EditText urlEditText = view.findViewById(R.id.urlEditText);
        String savedUrl = sharedPreferences.getString("URL", "");
        urlEditText.setText(savedUrl);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Connect To Server");
        builder.setMessage("Enter URL");
        builder.setView(view);
        builder.setPositiveButton("Connect", (dialog, which) -> {
            isConnected = true;
            connectImageView.setBackgroundResource(R.drawable.ic_disconnect);
            connectionProgressBar.setVisibility(View.VISIBLE);
            webView.setWebViewClient(new MyWebViewClient());
            String url = urlEditText.getText().toString();
            sharedPreferences.edit().putString("URL", url).apply();
            webView.loadUrl(url + ":8000");
            startStreaming(url);
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public void startStreaming(String url) {
        recordStatus = true;
        Thread streamThread = new Thread(() -> {
            try {
                int sampleRate = 44100;
                int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                DatagramSocket socket = new DatagramSocket();
                Log.d("VS", "Socket Created");

                byte[] buffer = new byte[minBufSize];
                Log.d("VS", "Buffer created of size " + minBufSize);

                DatagramPacket packet;
                final InetAddress destination = InetAddress.getByName(url);
                Log.d("VS", "Address retrieved");

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    return;

                audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize * 10);
                Log.d("VS", "Recorder initialized");

                audioRecorder.startRecording();
                while (recordStatus) {
                    audioRecorder.read(buffer, 0, buffer.length);
                    packet = new DatagramPacket (buffer, buffer.length, destination, audioPort);
                    socket.send(packet);
                }
            } catch (Exception e) {
                Log.d("log", e.getMessage());
            }
        });
        streamThread.start();
    }

    private void recordImageViewOnClickAction() {
        if (isRecording) {
            recorder.stopScreenRecording();
            recordImageView.setBackgroundResource(R.drawable.ic_record);
            isRecording = false;
        } else {
            startRecordingScreen();
        }
    }

    private void settingsImageViewOnClickAction() {
        final View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        Spinner timeSpinner = view.findViewById(R.id.timeSpinner);
        String[] items = new String[]{"Off", "1 minute", "3 minutes", "5 minutes"};
        ArrayAdapter<String> itemsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        itemsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(itemsAdapter);

        int savedTime = sharedPreferences.getInt("AutoSave", -1);
        timeSpinner.setSelection((savedTime + 1) / 2);

        final int[] time = new int[1];
        timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                time[0] = 2 * timeSpinner.getSelectedItemPosition() - 1;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        builder.setView(view);
        builder.setPositiveButton("Update", (dialog, which) -> {
            sharedPreferences.edit().putInt("AutoSave", time[0]).apply();

            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public class MyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            connectionProgressBar.setVisibility(View.GONE);
        }
    }
}