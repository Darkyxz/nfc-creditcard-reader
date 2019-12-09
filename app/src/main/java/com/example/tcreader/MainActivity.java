package com.example.tcreader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.acs.audiojack.AesTrackData;
import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.DukptReceiver;
import com.acs.audiojack.DukptTrackData;
import com.acs.audiojack.Status;
import com.acs.audiojack.Track1Data;
import com.acs.audiojack.Track2Data;
import com.acs.audiojack.TrackData;
import com.airbnb.lottie.LottieAnimationView;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {


    //Variables
    public static final String DEFAULT_MASTER_KEY_STRING =
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00";
    public static final String DEFAULT_AES_KEY_STRING =
            "4E 61 74 68 61 6E 2E 4C 69 20 54 65 64 64 79 20";
    public static final String DEFAULT_IKSN_STRING =
            "FF FF 98 76 54 32 10 E0 00 00";
    public static final String DEFAULT_IPEK_STRING =
            "6A C2 92 FA A1 31 5B 4D 85 8A B3 A3 D7 D5 93 3A";

    private boolean mFirmwareVersionReady;
    private boolean mResultReady;
    private AudioManager mAudioManager;
    private AudioJackReader mReader;
    private Context mContext = this;
    private ProgressDialog mProgress;
    private Object mResponseEvent = new Object();
    private AudioFocusHelper mAudioFocusHelper;
    private static final int REQUEST_RECORD_AUDIO = 1;
    private boolean mPermissionDenied;
    private boolean mStatusReady;
    private Status mStatus;
    private int mSleepTimeout = 20;
    private DukptReceiver mDukptReceiver = new DukptReceiver();
    TextView ccNumber;
    TextView ccName;
    TextView expireYear;
    TextView expireMonth;
    LottieAnimationView lottie;
    private byte[] mMasterKey = new byte[16];
    private byte[] mAesKey = new byte[16];
    private byte[] mNewMasterKey = new byte[16];
    private byte[] mIksn = new byte[10];
    private byte[] mIpek = new byte[16];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ccNumber = findViewById(R.id.creditCardNumber);
        expireMonth = findViewById(R.id.mesVencimiento);
        lottie= findViewById(R.id.layout_lottie);

        expireYear = findViewById(R.id.anioVenciminto);
        ccName = findViewById(R.id.titular);
        findViewById(R.id.layout).setVisibility(View.GONE);


        lottie.setVisibility(View.INVISIBLE);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mReader = new AudioJackReader(mAudioManager, true);
        mAudioFocusHelper = new AudioFocusHelper(mAudioManager, mReader);

        /* Register the headset plug receiver. */
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(mHeadsetPlugReceiver, filter);

        mProgress = new ProgressDialog(mContext);
        mProgress.setCancelable(false);
        mProgress.setIndeterminate(true);
        mReader.setSleepTimeout(mSleepTimeout);
        mReader.setOnStatusAvailableListener(new OnStatusAvailableListener());
        mReader.setOnTrackDataNotificationListener(new OnTrackDataNotificationListener());
        mReader.setOnTrackDataAvailableListener(new OnTrackDataAvailableListener());


        toByteArray(DEFAULT_MASTER_KEY_STRING, mNewMasterKey);
        toByteArray(DEFAULT_MASTER_KEY_STRING, mMasterKey);
        toByteArray(DEFAULT_AES_KEY_STRING, mAesKey);
        toByteArray(DEFAULT_IKSN_STRING, mIksn);
        toByteArray(DEFAULT_IPEK_STRING, mIpek);


        turnOn();
        clearCcData();
    }


    public void turnOn(){
        Button btnLector = findViewById(R.id.btnTurnOn);
        btnLector.setOnClickListener(new OnLectorListener());
    }

    public void clearCcData(){

        final Button limpiarD = findViewById(R.id.limpiarDatos);
        limpiarD.setFocusableInTouchMode(true);
        limpiarD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findViewById(R.id.layout).setVisibility(View.GONE);
                findViewById(R.id.layout_lottie).setVisibility(View.GONE);
                ccName.setText("");
                ccNumber.setText("");
                expireYear.setText("");
                expireMonth.setText("");
                limpiarD.requestFocus();
            }
        });
    }

    //Click listener para encender lector
    private class OnLectorListener implements View.OnClickListener{



            private class OnResetCompleteListener implements
                    AudioJackReader.OnResetCompleteListener {

                @Override
                public void onResetComplete(AudioJackReader reader) {

                    /* Get the status. */
                    mStatusReady = false;
                    mResultReady = false;
                    if (!reader.getStatus()) {

                        /* Show the request queue error. */
                        showRequestQueueError();

                    } else {

                        /* Show the status. */
                        showStatus();
                    }

                    /* Hide the progress. */
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mProgress.dismiss();
                        }
                    });
                }
            }


        @Override
        public void onClick(View v) {

            /* Check the reset volume. */
            if (!checkResetVolume()) {
                return;
            }


            /* Show the progress. */
            mProgress.setMessage("Encendiendo lector..");
            mProgress.show();

            /* Reset the reader. */
            mReader.reset(new OnResetCompleteListener());


        }


    }


    //---------------------------Necesarios

    @Override
    protected void onDestroy() {

        /* Unregister the headset plug receiver. */
        unregisterReceiver(mHeadsetPlugReceiver);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Request record audio permission. */
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (!mPermissionDenied) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO);
            } else {
                finish();
            }
        } else {
            if (!mAudioFocusHelper.requestFocus()) {
                showMessageDialog("Error", "Error");
            } else {
                mReader.start();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mProgress.dismiss();
        mReader.stop();
        mAudioFocusHelper.abandonFocus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if ((grantResults.length > 0)
                    && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                if (!mAudioFocusHelper.requestFocus()) {
                    showMessageDialog("error", "message_audiofocus_request_failed");
                } else {
                    mReader.start();
                }
            } else {
                mPermissionDenied = true;
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }



    private void showRequestQueueError() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                /* Show the request queue error. */
                Toast.makeText(mContext, "No se puede encender el lector",
                        Toast.LENGTH_LONG).show();
            }
        });
    }



    private boolean checkResetVolume() {

        boolean ret = true;

        int currentVolume = mAudioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);

        int maxVolume = mAudioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (currentVolume < maxVolume) {

            showMessageDialog("error", "Suba el volumen al maximo, por favor");
            ret = false;
        }

        return ret;
    }

    //Cambiar los r.id.string resources
    private void showMessageDialog(String titleId, String messageId) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        builder.setMessage(messageId)
                .setTitle(titleId)
                .setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                dialog.dismiss();
                            }
                        });

        builder.show();
    }

    private final BroadcastReceiver mHeadsetPlugReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {

                boolean plugged = (intent.getIntExtra("state", 0) == 1);

                /* Mute the audio output if the reader is unplugged. */
                mReader.setMute(!plugged);
            }
        }
    };

    private void showStatus() {

        synchronized (mResponseEvent) {

            /* Wait for the status. */
            while (!mStatusReady && !mResultReady) {

                try {
                    mResponseEvent.wait(10000);
                } catch (InterruptedException e) {
                }

                break;
            }

            if (mStatusReady) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        Toast.makeText(mContext,"Deslice su tarjeta, por favor",
                                Toast.LENGTH_LONG).show();
                    }
                });

            } else if (mResultReady) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        Toast.makeText(mContext,"No se pudo iniciar el lector",
                                Toast.LENGTH_LONG).show();
                    }
                });

            } else {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        /* Show the timeout. */
                        Toast.makeText(mContext, "No se pudo iniciar el lector.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            mStatusReady = false;
            mResultReady = false;
        }
    }


    //listeners
    private class OnStatusAvailableListener implements
            AudioJackReader.OnStatusAvailableListener {

        @Override
        public void onStatusAvailable(AudioJackReader reader, Status status) {

            synchronized (mResponseEvent) {

                /* Store the status. */
                mStatus = status;

                /* Trigger the response event. */
                mStatusReady = true;
                mResponseEvent.notifyAll();
            }
        }
    }


    private class OnTrackDataNotificationListener implements
            AudioJackReader.OnTrackDataNotificationListener {

        private Timer mTimer;

        @Override
        public void onTrackDataNotification(AudioJackReader reader) {

            /* Show the progress. */
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    mProgress.setMessage("Leyendo tarjeta...");
                    mProgress.show();
                }
            });

            /* Dismiss the progress after 5 seconds. */
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {

                    mProgress.dismiss();
                    mTimer.cancel();
                }
            }, 5000);
        }
    }

    private class OnTrackDataAvailableListener implements
            AudioJackReader.OnTrackDataAvailableListener {

        private Track1Data mTrack1Data;
        private Track2Data mTrack2Data;
        private Track1Data mTrack1MaskedData;
        private Track2Data mTrack2MaskedData;
        private String mTrack1MacString;
        private String mTrack2MacString;
        private String mBatteryStatusString;
        private String mKeySerialNumberString;
        private int mErrorId;

        @Override
        public void onTrackDataAvailable(AudioJackReader reader,
                                         TrackData trackData) {

            mTrack1Data = new Track1Data();
            mTrack2Data = new Track2Data();
            mTrack1MaskedData = new Track1Data();
            mTrack2MaskedData = new Track2Data();
            mTrack1MacString = "";
            mTrack2MacString = "";
            mKeySerialNumberString = "";

            /* Hide the progress. */
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mProgress.dismiss();
                }
            });

            if ((trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)
                    && (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)) {
                mErrorId = 3;
            } else if (trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS) {
                mErrorId = 2;
            } else if (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS) {
                mErrorId = 1;
            }

            /* Show the track error. */
            if ((trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)
                    || (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)) {
/*
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Cargando", "Intentando leer tarjeta");
                    }
                });
                */

            }
            /* Show the track data. */
            if (trackData instanceof AesTrackData) {
                showAesTrackData((AesTrackData) trackData);
            } else if (trackData instanceof DukptTrackData) {
                showDukptTrackData((DukptTrackData) trackData);
            }
        }

        private void showAesTrackData(AesTrackData trackData) {

            byte[] decryptedTrackData = null;

            /* Decrypt the track data. */
            try {

                decryptedTrackData = aesDecrypt(mAesKey,
                        trackData.getTrackData());

            } catch (GeneralSecurityException e) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Error",
                                "Error al desencriptar");
                    }
                });

                /* Show the track data. */
                showTrackData();
                return;
            }

            /* Verify the track data. */
            if (!mReader.verifyData(decryptedTrackData)) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Error",
                                "message_track_data_error_checksum");
                    }
                });

                /* Show the track data. */
                showTrackData();
                return;
            }

            /* Decode the track data. */
            mTrack1Data.fromByteArray(decryptedTrackData, 0,
                    trackData.getTrack1Length());
            mTrack2Data.fromByteArray(decryptedTrackData, 79,
                    trackData.getTrack2Length());

            /* Show the track data. */
            showTrackData();
        }


        private void showDukptTrackData(DukptTrackData trackData) {

            int ec = 0;
            int ec2 = 0;
            byte[] track1Data = null;
            byte[] track2Data = null;
            String track1DataString = null;
            String track2DataString = null;
            byte[] key = null;
            byte[] dek = null;
            byte[] macKey = null;
            byte[] dek3des = null;

            mKeySerialNumberString = toHexString(trackData.getKeySerialNumber());
            mTrack1MacString = toHexString(trackData.getTrack1Mac());
            mTrack2MacString = toHexString(trackData.getTrack2Mac());
            mTrack1MaskedData.fromString(trackData.getTrack1MaskedData());
            mTrack2MaskedData.fromString(trackData.getTrack2MaskedData());

            /* Compare the key serial number. */
            if (!DukptReceiver.compareKeySerialNumber(mIksn,
                    trackData.getKeySerialNumber())) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Error",
                                "message_track_data_error_ksn");
                    }
                });

                /* Show the track data. */
                showTrackData();
                return;
            }

            /* Get the encryption counter from KSN. */
            ec = DukptReceiver.getEncryptionCounter(trackData
                    .getKeySerialNumber());

            /* Get the encryption counter from DUKPT receiver. */
            ec2 = mDukptReceiver.getEncryptionCounter();

            /*
             * Load the initial key if the encryption counter from KSN is less
             * than the encryption counter from DUKPT receiver.
             */
            if (ec < ec2) {

                mDukptReceiver.loadInitialKey(mIpek);
                ec2 = mDukptReceiver.getEncryptionCounter();
            }

            /*
             * Synchronize the key if the encryption counter from KSN is greater
             * than the encryption counter from DUKPT receiver.
             */
            while (ec > ec2) {

                mDukptReceiver.getKey();
                ec2 = mDukptReceiver.getEncryptionCounter();
            }

            if (ec != ec2) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Error",
                                "message_track_data_error_ec");
                    }
                });

                /* Show the track data. */
                showTrackData();
                return;
            }

            key = mDukptReceiver.getKey();
            if (key == null) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        /* Show the timeout. */
                        Toast.makeText(
                                mContext,
                                "The maximum encryption count had been reached.",
                                Toast.LENGTH_LONG).show();
                    }
                });

                /* Show the track data. */
                showTrackData();
                return;
            }

            dek = DukptReceiver.generateDataEncryptionRequestKey(key);
            macKey = DukptReceiver.generateMacRequestKey(key);
            dek3des = new byte[24];

            /* Generate 3DES key (K1 = K3) */
            System.arraycopy(dek, 0, dek3des, 0, dek.length);
            System.arraycopy(dek, 0, dek3des, 16, 8);

            try {

                if (trackData.getTrack1Data() != null) {

                    /* Decrypt the track 1 data. */
                    track1Data = tripleDesDecrypt(dek3des,
                            trackData.getTrack1Data());

                    /* Generate the MAC for track 1 data. */
                    mTrack1MacString += " ("
                            + toHexString(DukptReceiver.generateMac(macKey,
                            track1Data)) + ")";

                    /* Get the track 1 data as string. */
                    track1DataString = new String(track1Data, 1,
                            trackData.getTrack1Length(), "US-ASCII");

                    /* Divide the track 1 data into fields. */
                    mTrack1Data.fromString(track1DataString);
                }

                if (trackData.getTrack2Data() != null) {

                    /* Decrypt the track 2 data. */
                    track2Data = tripleDesDecrypt(dek3des,
                            trackData.getTrack2Data());

                    /* Generate the MAC for track 2 data. */
                    mTrack2MacString += " ("
                            + toHexString(DukptReceiver.generateMac(macKey,
                            track2Data)) + ")";

                    /* Get the track 2 data as string. */
                    track2DataString = new String(track2Data, 1,
                            trackData.getTrack2Length(), "US-ASCII");

                    /* Divide the track 2 data into fields. */
                    mTrack2Data.fromString(track2DataString);
                }

            } catch (GeneralSecurityException e) {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        showMessageDialog("Error",
                                "message_track_data_error_decrypted");
                    }
                });

            } catch (UnsupportedEncodingException e) {
            }

            /* Show the track data. */
            showTrackData();
        }

        private void showTrackData() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    LottieAnimationView  lottie = findViewById(R.id.layout_lottie);

                    lottie.setVisibility(View.VISIBLE);
                    lottie.playAnimation();

                    findViewById(R.id.layout).setVisibility(View.VISIBLE);
                    ccNumber.setText(mTrack1Data.getPrimaryAccountNumber() + mTrack1MaskedData.getPrimaryAccountNumber());
                    ccName.setText(mTrack1Data.getName()+mTrack1MaskedData.getName());
                    expireYear.setText(mTrack1Data.getExpirationDate().substring(0,2));
                    expireMonth.setText(mTrack1Data.getExpirationDate().substring(2,4));

                }

            });


        }
    }


    private String toHexString(byte[] buffer) {

        String bufferString = "";

        if (buffer != null) {

            for (int i = 0; i < buffer.length; i++) {

                String hexChar = Integer.toHexString(buffer[i] & 0xFF);
                if (hexChar.length() == 1) {
                    hexChar = "0" + hexChar;
                }

                bufferString += hexChar.toUpperCase(Locale.US) + " ";
            }
        }

        return bufferString;
    }



    private byte[] aesDecrypt(byte key[], byte[] input)
            throws GeneralSecurityException {

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[16]);

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        return cipher.doFinal(input);
    }

    private byte[] tripleDesDecrypt(byte[] key, byte[] input)
            throws GeneralSecurityException {

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[8]);

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        return cipher.doFinal(input);
    }
    private int toByteArray(String hexString, byte[] byteArray) {

        char c = 0;
        boolean first = true;
        int length = 0;
        int value = 0;
        int i = 0;

        for (i = 0; i < hexString.length(); i++) {

            c = hexString.charAt(i);
            if ((c >= '0') && (c <= '9')) {
                value = c - '0';
            } else if ((c >= 'A') && (c <= 'F')) {
                value = c - 'A' + 10;
            } else if ((c >= 'a') && (c <= 'f')) {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {

                if (first) {

                    byteArray[length] = (byte) (value << 4);

                } else {

                    byteArray[length] |= value;
                    length++;
                }

                first = !first;
            }

            if (length >= byteArray.length) {
                break;
            }
        }

        return length;
    }


}
