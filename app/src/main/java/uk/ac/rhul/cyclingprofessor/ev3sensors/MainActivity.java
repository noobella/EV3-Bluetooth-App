package uk.ac.rhul.cyclingprofessor.ev3sensors;


import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = "EV3Sensors::Activity";
    private static final int SHOWN_IPV4 = 1;
    private static final int PERMISSION_REQUEST_CODE = 0;
    private static final String MIME_TEXT_PLAIN = "text/plain";

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA, Manifest.permission.INTERNET, Manifest.permission.NFC};

    private EditText mSend;
    private final List<String> receivedItems = new ArrayList<>();
    private List<List<EditText>> sudokuNumbers = new ArrayList<>();

    private Server server;
    private ArrayAdapter<String> listAdapter;


    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar); // Attaching the layout to the toolbar object
        setSupportActionBar(toolbar);

        Button mSendButton = findViewById(R.id.button_send);
        mSend = findViewById(R.id.send);
        ListView mReceived = findViewById(R.id.received);
        listAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                receivedItems);
        mReceived.setAdapter(listAdapter);
        mHandler.init();


        //ADDED CODE

        //Creates the Sudoku grid using nested for loops
        //LinearLayout defined in activity_main.xml is obtained- will be the outline of the grid
        LinearLayout gridLayout = findViewById(R.id.grid_layout);
        //For loop will create nine rows for the grid
        for(int i = 0; i < 9; i++){
            //New row created
            LinearLayout rowLayout = new LinearLayout(this);
            //Width and height specified and set
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLayout.setLayoutParams(rowParams);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            //Row added to the main layout
            gridLayout.addView(rowLayout);
            //Row added to the arraylist sudokuNumbers
            sudokuNumbers.add(new ArrayList<>());

            //Variable to set the maximum amount of characters allowed per input
            int maxLength = 1;
            //For loop to add 9 edit text boxes to each row
            for(int j = 0; j < 9; j++) {
                //Creates edit text box
                EditText et = new EditText(this);
                //Width and height specified and set
                LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(100, 100);
                et.setLayoutParams(lParams);
                //Border is set (black outline)
                et.setBackgroundResource(R.drawable.border);
                //Text gravity set to center
                et.setGravity(Gravity.CENTER);
                //Limits input to numbers only
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                //Restricts input to one digit per text box
                et.setFilters(new InputFilter[] {new InputFilter.LengthFilter(maxLength)});
                //Edit text is added to row layout
                rowLayout.addView(et);
                //Edit text is added to arraylist
                sudokuNumbers.get(i).add(et);
            }

        }

        checkPermissions();

        //When send button is pressed, values from grid are obtained and stored in array
        //Sudoku is then solved, stored into a string and sent
        mSendButton.setOnClickListener(v -> {
            // Send a message using content of the edit text widget
            String message = "";
            //Array created to store values
            int[][] sudokuGrid = new int[9][9];
            //For loop used to get values from edit text arraylist and store into array
            for(int i = 0 ; i < 9; i++){
                for(int j = 0; j < 9; j++){
                    EditText et = sudokuNumbers.get(i).get(j);
                    //If no value in text box, processed as 0 as sudoku solver assumes 0 to mean no number
                    if(et.getText().toString().equals("")){
                        sudokuGrid[i][j] = 0;
                    }else{
                        sudokuGrid[i][j] = Integer.parseInt(et.getText().toString());
                    }
                }
            }

            //If the sudoku is solvable, solve it and store all numbers into a single string
            if(Sudoku.solve(sudokuGrid)){
                for(int i = 0; i < 9; i++){
                    for(int j = 0; j < 9; j++){
                        String number = String.valueOf(sudokuGrid[i][j]);
                        message += number;
                    }
                }
            }
            mSend.setText(message);
            //Solved sudoku is sent
            sendMessage(message);
        });

        //END OF ADDED CODE

    }

    @Override
    public void onPause() {
        super.onPause();
        server.stop();
        if (nfcAdpt != null) {
            nfcAdpt.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        server.start();
        if (nfcAdpt != null) {
            nfcAdpt.enableForegroundDispatch(
                    this,
                    nfcPendingIntent,
                    intentFiltersArray,
                    null);
        }

    }

    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Toolbar tb = findViewById(R.id.toolbar);
        tb.inflateMenu(R.menu.menu_connect);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_addresses:
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(this, ConnectionListActivity.class);
                startActivityForResult(serverIntent, SHOWN_IPV4);
                return true;
            case R.id.about_app:
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("EV3 Sudoku Solver").setMessage(getString(R.string.about_message)).show();
                return true;
            case R.id.exit_app:
                finish();
                moveTaskToBack(true);
                return true;
            default: {
                break;
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode== SHOWN_IPV4) && (resultCode != Activity.RESULT_OK)) {
            showExitDialog(getString(R.string.no_network_available));
        }
    }

    private void showExitDialog(String text) {
        FragmentManager fm = getSupportFragmentManager();
        ExitWithMessageDialogFragment alertDialog = ExitWithMessageDialogFragment.newInstance(text);
        alertDialog.show(fm, "exit_fragment");

    }

    public void exitApplication() {
        finish();
    }


    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     * <p>
     * Modified from developer.here.com
     */
    private void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request required (and missing) permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[0]);
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        } else {
            // We already have them all - whoopee
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(PERMISSION_REQUEST_CODE, REQUIRED_SDK_PERMISSIONS, grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Required permission '" + permissions[index]
                            + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            initialize();
        }
    }

    private void initialize() {
        //mOpenCvCameraView.setCvCameraViewListener(new CV_Camera(this));
        server = new Server(this, mHandler);
        server.start();
        startNFC();
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        final ActionBar actionBar = getSupportActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * Thanks to Alex Lockwood - Android Design Patterns for the heads up about the huge memory leak by using an anonymous Handler inner class.
     * https://www.androiddesignpatterns.com/2013/01/inner-class-handler-memory-leak.html
     **/
    private static class ServerHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;
        private String mConnMsg = null;

        ServerHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        void init() {
            mConnMsg = mActivity.get().getString(R.string.title_not_connected);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity == null) {
                // We cannot handle events if there is no-one who cares about them.
                return;
            }
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case Server.STATE_CONNECTED:
                            activity.setStatus(mConnMsg);
                            break;
                        case Server.STATE_LISTEN:
                        case Server.STATE_NONE:
                            activity.setStatus(activity.getString(R.string.title_not_connected));
                            break;
                    }
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage;
                    try {
                        readMessage = new String(readBuf, 0, msg.arg1);
                    } catch (StringIndexOutOfBoundsException e) {
                        Log.e(TAG, "could not read string, is the program running?", e);
                        activity.setStatus("No EV3 Connected");
                        System.exit(-1);
                        break;
                    }

                    activity.receivedItems.add(0, readMessage);
                    activity.listAdapter.notifyDataSetChanged();
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    String device = msg.getData().getString(Constants.DEVICE_NAME);
                    mConnMsg = activity.getString(R.string.title_connected_to, device);
                    Toast.makeText(activity, mConnMsg, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(activity, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    /**
     * The Handler that gets information back from the Server object
     */
    private final ServerHandler mHandler = new ServerHandler(this);

    private NfcAdapter nfcAdpt;
    private PendingIntent nfcPendingIntent;
    private IntentFilter[] intentFiltersArray;

    private void startNFC() {
        nfcAdpt = NfcAdapter.getDefaultAdapter(this);
        // Check if the smartphone has NFC - even if it does not we should just continue - but we will receive no intents.
        if (nfcAdpt == null) {
            return;
        }
        // Check if NFC is enabled
        if (!nfcAdpt.isEnabled()) {
            Toast.makeText(this, "Enable NFC before using the app", Toast.LENGTH_LONG).show();
            return;
        }
        Context context = getApplicationContext();

        Intent nfcIntent = new Intent(context, MainActivity.class);
        nfcIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        nfcPendingIntent = PendingIntent.getActivity(context, 0, nfcIntent, 0);

        IntentFilter tagIntentFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            tagIntentFilter.addDataType(MIME_TEXT_PLAIN);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }
        intentFiltersArray = new IntentFilter[]{tagIntentFilter};
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "Found a Tag");

        String action = intent.getAction();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            String type = intent.getType();
            if (MIME_TEXT_PLAIN.equals(type)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                new NdefReaderTask(this).execute(tag);
            }
        }
    }

    /**
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     * @author Ralf Wondratschek
     */
    private static class NdefReaderTask extends AsyncTask<Tag, Void, String> {
        final WeakReference<MainActivity> mActivity;

        NdefReaderTask(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();
            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        Log.d(MainActivity.TAG, "NFC Processing");
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(MainActivity.TAG, "Unsupported Encoding", e);
                    }
                }
            }
            return null;
        }

        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            /*
             * See NFC forum specification for "Text Record Type Definition" at 3.2.1
             *
             * http://www.nfc-forum.org/specs/
             *
             * bit_7 defines encoding
             * bit_6 reserved for future use, must be 0
             * bit_5..0 length of IANA language code
             */

            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

            // Get the Language Code
            int languageCodeLength = payload[0] & 63;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                MainActivity activity = mActivity.get();
                if (activity != null) {
                    activity.sendMessage("NFC: " + result);
                }
            }
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (server.getState() != Server.STATE_CONNECTED) {
            // Silently ignore messages.
            Log.e(TAG, "no connection");
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            server.write(send);
        }
    }


}