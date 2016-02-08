package com.socialdiabetes.freestylelibre;

        import java.io.File;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.OutputStreamWriter;
        import java.text.SimpleDateFormat;
        import java.util.Arrays;
        import java.util.Date;

        import android.app.Activity;
        import android.app.PendingIntent;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.media.MediaPlayer;
        import android.media.MediaPlayer.OnCompletionListener;
        import android.nfc.NfcAdapter;
        import android.nfc.Tag;
        import android.nfc.tech.NfcV;
        import android.os.AsyncTask;
        import android.os.Bundle;
        import android.os.Vibrator;
        import android.util.Log;
        import android.widget.TextView;
        import android.widget.Toast;

        // playing with scheduling
        import static java.util.concurrent.TimeUnit.*;
        import java.util.concurrent.Executors;
        import java.util.concurrent.ScheduledExecutorService;
        import java.util.concurrent.ScheduledFuture;
        import com.socialdiabetes.freestylelibre.NFCWatchdogRefresher;

/**
 *
 * Activity for reading data from FreeStyleLibre Tag
 *
 */
public class Abbott extends Activity {

    public static final String MIME_TEXT_PLAIN = "text/plain";
    public static final boolean USE_SI = true;
    public static final double SI_FACTOR = 0.05551;
    public static final int LOOP_SECONDS = 300;

    protected NfcAdapter mNfcAdapter;

    private String lectura, buffer;
    private double currentGlucose = 0f;
    private TextView tvResult;

    public static boolean play_sounds = false;
    public static boolean write_file = false;
    public static int vibrate_start = 0;
    public static int vibrate_ok = 0;
    public static int vibrate_err = 0;

    // store last Tag for periodic check
    Tag tag = null;
    ScheduledFuture b;

    /**
     * TODO: look at https://groups.google.com/forum/#!topic/android-developers/8-17f6ZLYJY
     * instead of this codswallop.
     */
    protected void setupNfc() {
        Log.d("sd","Setting NFC adaptor.");
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
        } else if (!mNfcAdapter.isEnabled()) {
            Toast.makeText(this, "NFC is disabled.", Toast.LENGTH_LONG).show();
            mNfcAdapter = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_abbott);
        tvResult = (TextView)findViewById(R.id.result);

        setupNfc();
        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            finish();
            return;
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            // We want to handle everything else under this intent, for now
            // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            Log.d("sd", "NfcAdapter.ACTION_TECH_DISCOVERED");
            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            // String searchedTech = NfcV.class.getName();

            // TODO: integrate with below.
            new NfcVReaderTask().execute(tag);

            // Test ''.
            if (tag != this.tag) {
                this.tag = tag;
                if (b != null) b.cancel(true);
            }
            b = new ScheduledRead().readTag();
        }
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link Activity} (BaseActivity?) requesting to stop
     *                 the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Schedule future reads without removing phone from the tag.
     * See: http://developer.android.com/reference/java/util/concurrent/ScheduledExecutorService.html
     */
    class ScheduledRead {
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        public ScheduledFuture readTag() {
            final Runnable runread = new Runnable() {
                public void run() {
                    try {
                        if (tag != null) {
                            Log.d("sd", "Attempting scheduled read of " + tag.getId());
                            new NfcVReaderTask().execute(tag);
                        }
                    } catch (Exception e) {
                        Log.d("sd", "Cancelling schedule; no tag.");
                        scheduler.shutdown(); // ??
                    }
                }
            };
            final ScheduledFuture schedHandle =
                scheduler.scheduleAtFixedRate(runread, LOOP_SECONDS, LOOP_SECONDS, SECONDS);
            // Self-cancelling with the below:
            /*scheduler.schedule(new Runnable() {
                public void run() { schedHandle.cancel(true); }
            }, 60 * 60, SECONDS);*/
            return schedHandle;
        }
    }

    /**
     *
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     */
    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected void onPostExecute(String result) {
            // TODO: vibrate prefs: (start, OK, err) lengths
            NFCWatchdogRefresher.holdConnection(NfcV.get(tag), (LOOP_SECONDS*60)-1);
            Log.i("sd", "Handling result " +result);
            if (result != null && vibrate_ok > 0) { // TODO: if OK && ...
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(vibrate_ok); // 1000
            } else if (result == null && vibrate_err > 0) { // TODO: if err && ...
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vibrator.vibrate(vibrate_err); // 1000
            } else {
                Log.d("sd", "Vibrate disabled for sleepytime...");
                //Abbott.this.finish();
            }
        }

        @Override
        protected void onPreExecute() {
            // setupNfc();
            NFCWatchdogRefresher.stopHoldingConnection();
        }

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            Double result = null;

            NfcV nfcvTag = NfcV.get(tag);
            Log.d("sd", "Enter NdefReaderTask: " + nfcvTag.toString());

            Log.d("sd", "Tag ID: "+tag.getId());


            try {
                nfcvTag.connect();
            } catch (IOException e) {
                errorToast("Error opening NFC connection!");
                return null;
            }

            lectura = "";

            byte[][] bloques = new byte[40][8];
            byte[] allBlocks = new byte[40*8];

            Log.d("sd", "---------------------------------------------------------------");
            Log.d("sd", "nfcvTag ID: "+nfcvTag.getDsfId());

            //Log.d("sd", "getMaxTransceiveLength: "+nfcvTag.getMaxTransceiveLength());
            try {

                // Get system information (0x2B)
                byte[] cmd = new byte[] {
                        (byte)0x00, // Flags
                        (byte)0x2B // Command: Get system information
                };
                byte[] systeminfo = nfcvTag.transceive(cmd);

                //Log.d("sd", "systeminfo: "+systeminfo.toString()+" - "+systeminfo.length);
                //Log.d("sd", "systeminfo HEX: "+bytesToHex(systeminfo));

                systeminfo = Arrays.copyOfRange(systeminfo, 2, systeminfo.length - 1);

                byte[] memorySize = { systeminfo[6], systeminfo[5]};
                Log.v("sd", "Memory Size: "+bytesToHex(memorySize)+" / "+ Integer.parseInt(bytesToHex(memorySize).trim(), 16 ));

                byte[] blocks = { systeminfo[8]};
                Log.v("sd", "blocks: " + bytesToHex(blocks) + " / " + Integer.parseInt(bytesToHex(blocks).trim(), 16));

                int totalBlocks = Integer.parseInt(bytesToHex(blocks).trim(), 16);

                for(int i=3; i <= 40; i++) { // Leer solo los bloques que nos interesan
                	/*
	                cmd = new byte[] {
	                    (byte)0x00, // Flags
	                    (byte)0x23, // Command: Read multiple blocks
	                    (byte)i, // First block (offset)
	                    (byte)0x01  // Number of blocks
	                };
	                */
                    // Read single block
                    cmd = new byte[] {
                            (byte)0x00, // Flags
                            (byte)0x20, // Command: Read multiple blocks
                            (byte)i // block (offset)
                    };

                    byte[] oneBlock = nfcvTag.transceive(cmd);
                    Log.v("sd", "userdata: " + oneBlock.toString() + " - " + oneBlock.length);
                    oneBlock = Arrays.copyOfRange(oneBlock, 1, oneBlock.length);
                    bloques[i-3] = Arrays.copyOf(oneBlock, 8);


                    Log.v("sd", "userdata HEX: " + bytesToHex(oneBlock));

                    lectura = lectura + bytesToHex(oneBlock)+"\r\n";
                }

                String s = "";
                for(int i=0;i<40;i++) {
                    Log.v("sd", bytesToHex(bloques[i]));
                    s = s + bytesToHex(bloques[i]);
                }

                Log.d("sd", "S: "+s);

                Log.d("sd", "Next read: "+s.substring(4,6));
                int current = Integer.parseInt(s.substring(4, 6), 16);
                Log.d("sd", "Next read: " + current);
                Log.d("sd", "Next historic read "+s.substring(6,8));

                String[] bloque1 = new String[16];
                String[] bloque2 = new String[32];
                Log.v("sd", "--------------------------------------------------");
                int ii=0;
                for (int i=8; i< 8+15*12; i+=12)
                {
                    Log.v("sd", s.substring(i,i+12));
                    bloque1[ii] = s.substring(i,i+12);

                    final String g = s.substring(i+2,i+4)+s.substring(i,i+2);

                    if (current == ii) {
                        currentGlucose = glucoseReading(Integer.parseInt(g,16));
                    }
                    ii++;


                }
                lectura = lectura + "Current approximate glucose " + currentGlucose;
                Log.i("sd", "Current approximate glucose " + currentGlucose);
                result = currentGlucose;

                Log.v("sd", "--------------------------------------------------");
                ii=0;
                for (int i=188; i< 188+31*12; i+=12)
                {
                    Log.v("sd", s.substring(i, i + 12));
                    bloque2[ii] = s.substring(i,i+12);
                    ii++;
                }
                Log.v("sd", "--------------------------------------------------");

            } catch (IOException e) {
                errorToast("Error reading NFC!");
                return null;
            }

            addText(lectura);

            try {
                nfcvTag.close();
            } catch (IOException e) {
                Log.w("sd", "Couldn't close tag. NBD.");
            }

            // TODO: if "play sound?" preference
            if (play_sounds) {
                MediaPlayer mp;
                mp = MediaPlayer.create(Abbott.this, R.raw.notification);
                mp.setSurface(null);
                mp.setOnCompletionListener(new OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mp.reset();
                        mp.release();
                        mp = null;
                    }
                });
                mp.start();
            }

            // TODO: if "write file?" preference
            if (write_file) {
                Date date = new Date();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                File myFile = new File("/sdcard/fsl_" + dateFormat.format(date) + ".log");
                try {
                    myFile.createNewFile();
                    FileOutputStream fOut = new FileOutputStream(myFile);
                    OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                    myOutWriter.append(lectura);
                    myOutWriter.close();
                    fOut.close();
                } catch (Exception e) {
                    Log.e("sd", "Error writing log file.");
                }
            }
            return result.toString();
        }

    }

    /**
     *  Makes a 'toast' runnable suitable for running on the UI thread.
     */
    private void errorToast(final String msg) {
        Log.e("sd", msg);
        Abbott.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addText(final String s)
    {
        Abbott.this.runOnUiThread(new Runnable() {
            public void run() {
                tvResult.setText(s);
            }
        });

    }

    private void GetTime(Long minutes){
        Long t3 = minutes;
        Long t4 = t3/1440;
        Long t5 = t3-(t4*1440);
        Long t6 = (t5/60);
        Long t7 = t5-(t6*60);
    }

    private double glucoseReading(int val) {
        // ((0x4531 & 0xFFF) / 6) - 37;
        int bitmask = 0x0FFF;
        double glucose = ((val & bitmask) / 6) - 37;
        if (USE_SI) {
            return (glucose * Abbott.SI_FACTOR);
        } else {
            return glucose;
        }
    }


}


