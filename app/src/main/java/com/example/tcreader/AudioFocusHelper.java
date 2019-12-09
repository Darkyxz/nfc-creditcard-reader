package com.example.tcreader;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import com.acs.audiojack.AudioJackReader;

class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
    private AudioManager mManager;
    private AudioJackReader mReader;
    private int mLastState;
    private AudioFocusRequest mFocusRequest;

    /**
     * Creates an instance of {@code AudioFocusHelper}.
     *
     * @param manager the manager
     * @param reader  the reader
     */
    public AudioFocusHelper(AudioManager manager, AudioJackReader reader) {

        mManager = manager;
        mReader = reader;
    }

    /**
     * Requests audio focus.
     *
     * @return {@code true} if the operation completed successfully, otherwise {@code false}.
     */
    public boolean requestFocus() {

        boolean ret = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {

            ret = (mManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        } else {

            if (mFocusRequest == null) {
                mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();
            }

            ret = (mManager.requestAudioFocus(mFocusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }

        return ret;
    }

    /**
     * Abandons audio focus.
     *
     * @return {@code true} if the operation completed successfully, otherwise {@code false}.
     */
    public boolean abandonFocus() {

        boolean ret = false;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {

            ret = (mManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        } else {

            if (mFocusRequest != null) {
                ret = (mManager.abandonAudioFocusRequest(mFocusRequest)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            }
        }

        return ret;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {

        switch (focusChange) {

            case AudioManager.AUDIOFOCUS_GAIN:
                switch (mLastState) {

                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        mReader.setMute(false);
                        break;

                    default:
                        mReader.start();
                        break;
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                mReader.stop();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mReader.setMute(true);
                break;

            default:
                break;
        }

        mLastState = focusChange;
    }



}
