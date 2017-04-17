package net.majorkernelpanic.streaming.rtp.unpacker;

import android.media.MediaCodec;
import android.util.Log;

import net.majorkernelpanic.streaming.ByteUtils;
import net.majorkernelpanic.streaming.InputStream;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Leaves on 2017/4/2.
 */

public class AACADTSUnpacker extends AbstractUnpacker implements Runnable {
    private static final String TAG = "AACADTSUnpacker";
    private Thread mThread;
    private MediaCodec mDecoder;
    private ByteBuffer[] mInputBuffers;
    private byte[] mADTSHeader;
    private InputStream.Config mConfig;
    private int mSeq = 0;

    public AACADTSUnpacker(MediaCodec decoder) {
        if (decoder != null) {
            mDecoder = decoder;
            mInputBuffers = decoder.getInputBuffers();
        }
        mADTSHeader = new byte[7];
    }

    @Override
    public void start() {
        if (mThread == null) {
            mThread = new Thread(this);
            mThread.setName("unpackThread");
            mThread.start();
        }
    }

    @Override
    public void stop() {
        if (mThread != null) {
            mSocket.close();
            mThread.interrupt();
            try {
                mThread.join();
            } catch (InterruptedException e) {
            }
            mThread = null;
        }
    }

    @Override
    public void run() {
        while (!mThread.isInterrupted()) {
            byte[] result;
            result = mSocket.read();
            if (result != null) {
                parse(result);
            }
        }
    }

    private void parse(byte[] rtpPacket) {
        if (mDecoder != null) {
            long timeStamp = ByteUtils.byteToLong(rtpPacket, 4, 4);
            int AUHeaderLength = (int) ByteUtils.byteToLong(rtpPacket, rtphl, 2);
            int AUSize = (int) ByteUtils.byteToLong(rtpPacket, rtphl + 2, 2);
            AUSize >>= 3;
            int AUIndex = (int) ByteUtils.byteToLong(rtpPacket, rtphl + 3, 1);
            boolean bitMark = (rtpPacket[1] & 0x80) == 0x80;
            if (bitMark) {
                int inputIndex = mDecoder.dequeueInputBuffer(-1);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = mInputBuffers[inputIndex];
                    inputBuffer.clear();
                    addADTStoPacket(mADTSHeader, AUSize + 7);
                    inputBuffer.put(mADTSHeader);
                    inputBuffer.put(rtpPacket, rtphl + 4, AUSize);
//                    if (mCount++ < 100) {
//                        inputBuffer.flip();
//                        byte[] temp = new byte[200];
//                        inputBuffer.get(temp, 0, AUSize + 7);
//                        ByteUtils.logByte(temp, 0,  AUSize + 7);
//                    }
                    Log.d(TAG, "count:" + mSeq++);
                    mDecoder.queueInputBuffer(inputIndex, 0, AUSize + 7, timeStamp, 0);
                } else {
                    Log.v(TAG, "No buffer available...");
                }
            } else {
                Log.d(TAG, "bitMark == false");
            }
        }
    }

    /**
     * 添加ADTS头
     *
     * @param packet
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4;
        int chanCfg = 2; // CPE
        if (mConfig != null) {
            profile = 2; // AAC LC
            freqIdx = mConfig.sampleRateIndex;
            chanCfg = mConfig.channelCount; // CPE
        }

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public void setConfig(InputStream.Config config) {
        mConfig = config;
        mSocket.setWaitingTimeout(1000000L / config.sampleRate);
    }
}
