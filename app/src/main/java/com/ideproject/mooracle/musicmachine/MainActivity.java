package com.ideproject.mooracle.musicmachine;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.ideproject.mooracle.musicmachine.adapters.PlaylistAdapter;
import com.ideproject.mooracle.musicmachine.models.Song;

public class MainActivity extends AppCompatActivity {


    public static final String EXTRA_SONG = "com.mooracle.intent.action.EXTRA_SONG";
    public static final String EXTRA_LIST_POSITION = "com.mooracle.intent.action.EXTRA_LIST_POS";

    public static final int REQUEST_FAVORITE = 0;//TODO: for revision!!
    public static final String EXTRA_FAVORITE = "com.mooracle.intent.action.EXTRA_FAVORITE";

    private PlaylistAdapter adapter;

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String KEY_SONG = "song";
    private boolean mBound = false; // this will track the connection is bound or not
    private Messenger serviceMessenger;
    private Messenger activityMessenger = new Messenger(new ActivityHandler(this));

    private Button mDownloadButton;
    private Button mPlayerButton;
    private ConstraintLayout constraintLayout;
    private NetworkConnectionReceiver receiver = new NetworkConnectionReceiver();

    //build anonymous class for service connection:
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            //in onServiceConnected we need to make sure to ask if the player is play or paused
            //thus we need a serviceMessenger to ask the question to PlayerService
            //this serviceMessenger use binder instead of handler to check for the first time it binds.
            //then we need to set another messenger (activityMessenger) as replyTo messenger.
            //activityMessenger use handler instead of binder since it will be handled by ActivityHandler instance
            //thus we need to define it in field definition

            mBound = true;
            serviceMessenger = new Messenger(binder);
            Message message = Message.obtain();
            message.arg1 = 2; //ask if play or not
            message.arg2 = 1; // arg2=1 means this is the first time connected do not change the play status!
            message.replyTo = activityMessenger;
            try {
                serviceMessenger.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };
    private BroadcastReceiver localReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //extract the boolean data from the broadcast
            boolean isConnected = intent.getBooleanExtra(NetworkConnectionReceiver.EXTRA_IS_CONNECTED, false);

            if (isConnected) {
                Snackbar.make(constraintLayout, "Network is connected", Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar.make(constraintLayout, "Network is disconnected", Snackbar.LENGTH_LONG).show();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDownloadButton = findViewById(R.id.downloadButton);
        mPlayerButton = findViewById(R.id.playerButton);
        //initiate constraint layout
        constraintLayout = findViewById(R.id.relativeLayout);
        //please ignore the naming of relative layout since it was originally relative

        //this was deleted since we use service now rather than thread handler to download the songs

        mDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Inside the OnClick method, let's start with a toast that says downloading.
                //Make sure to pick the Create a new Toast option,
                //then hit Enter to move to the text parameter, and write Downloading.
                //Then, let's make a call to a new method called downloadSong.
                //And let's use alt + enter to create this method inside our MainActivity class.
                Toast.makeText(MainActivity.this, "Downloading...", Toast.LENGTH_SHORT).show();

                //testIntents();
                downloadSongs();
            }
        });

        mPlayerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //check if it was bound
                if (mBound){

                    Intent intent = new Intent(MainActivity.this, PlayerService.class);
                    //send the song object to the PlayerService
                    intent.putExtra(EXTRA_SONG, Playlist.songs[0]);
                    startService(intent);

                    Message message = Message.obtain();
                    message.arg1 = 2; //ask if play or not
                    message.replyTo = activityMessenger;
                    //Note this message.replyTo is important to get the messenger (the same messenger as the one sent
                    //here) to be sent back to notify when the song is done playing
                    //without this we cannot access the same messenger as this since it cannot be public static!!
                    try {
                        serviceMessenger.send(message);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        adapter = new PlaylistAdapter(Playlist.songs, this);
        recyclerView.setAdapter(adapter);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
    }

    private void testIntents() {
        //Explicit Intent
//        Intent intent = new Intent(this, DetailActivity.class);
//        intent.putExtra(EXTRA_SONG, "Gradle, Gradle, Gradle");
//        startActivityForResult(intent, REQUEST_FAVORITE);

        //Implicit intent:
        Intent intent = new Intent((Intent.ACTION_VIEW));
        Uri geoLocation = Uri.parse("geo:0,0?q=-7.722552, 110.309338(Lestari)");
        intent.setData(geoLocation);
        //checking if there is app that can handle the request from the implicit intent:
        if (intent.resolveActivity(getPackageManager()) == null){
            //let's just put snack bar at the moment
            Snackbar.make(constraintLayout, "Sorry there is no apps which can handle the request",
                    Snackbar.LENGTH_LONG).show();
        }else {
            startActivity(intent);
        }
    }

    private void downloadSongs() {
        //int index = 0;
        for (Song song : Playlist.songs){


            Intent intent = new Intent(MainActivity.this, DownloadIntentService.class);
            intent.putExtra(KEY_SONG, song); //<- put song name as extra to be extracted later
            //intent.putExtra(EXTRA_LIST_POSITION, index);
            //lastly start the service
            startService(intent);
            //index += 1;
        }
    }

    public void setPlayerButtonText(String text){
        mPlayerButton.setText(text);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //make log that it was on Resume
        Log.i(TAG, "App is on the foreground");
        //this code is added to substitute the manifest broadcast receiver since it was already deprecated
        //the broadcast receiver is context abstract class thus can be directly from context mainActivity
        //we can use this
        //WARNING: this is different from the course video since the new abstract class register receiver is part
        //of the context now, thus it needs to register as this
        // first we need to instantiate intent filter and specify the filter in the manifest here:
        IntentFilter intentFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        this.registerReceiver(receiver, intentFilter);

        //create another intent filter for custom intent broadcasr
        IntentFilter customFilter = new IntentFilter(NetworkConnectionReceiver.NOTIFY_NETWORK_CHANGES);
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, customFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "App is in Background");
        //unregister receiver when it was on the background:
        this.unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound){
            unbindService(serviceConnection);
            mBound = false;
            //note: unbinding service will not change the mBound since onDisconnected only called in
            //extraordinary circumstances. Thus this class itself need to change mBound value.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FAVORITE) {
            if (resultCode == RESULT_OK){
                // handles the result
                boolean result = data.getBooleanExtra(EXTRA_FAVORITE, false);
                Log.d(TAG, "onActivityResult: isFavorite?" + result);
                int position = data.getIntExtra(EXTRA_LIST_POSITION, 0);
                //update the favorite status directly from data to match and notify adapter of changes
                Playlist.songs[position].setFavorite(result);
                adapter.notifyItemChanged(position);
            }
        }
    }
}
