package com.example.shenenchao.readercardno;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.acs.smartcard.Reader;

public class MainActivity extends ActionBarActivity {

    private Context mCxt = this;
    private static final String ACTION_USB_PERMISSION = "com.example.myreadcardtest.USB_PERMISSION";
    private static final String[] stateStrings = { "Unknown", "Absent",
            "Present", "Swallowed", "Powered", "Negotiable", "Specific" };
    private String TAG = "ReadCard";
    private static final int MAX_LINES = 25;
    private UsbManager mManager;
    private Reader mReader;
    private PendingIntent mPermissionIntent;
    private ArrayAdapter<String> mReaderAdapter;
    private ArrayAdapter<String> mSlotAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initDevice();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id=item.getItemId();
        if(id==R.id.action_settings){
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestPermission(){
        boolean requested=false;
        for (UsbDevice device:mManager.getDeviceList().values()){
            if(mReader.isSupported(device)){
                printLog(device.getDeviceName());
                requested = true;
                mManager.requestPermission(device, mPermissionIntent);
                break;
            }
        }
        if (!requested) {
            Toast.makeText(mCxt, "设备初始化错误，请重试！", Toast.LENGTH_SHORT).show();
        }
    }
    // 初始化读卡器，注册广播器
    private void initDevice() {
        // Get USB manager
        mManager = (UsbManager) mCxt.getSystemService(mCxt.USB_SERVICE);
        // Initialize reader
        mReader = new Reader(mManager);
        // Register receiver for USB permission
        mPermissionIntent = PendingIntent.getBroadcast(mCxt, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mCxt.registerReceiver(mReceiver, filter);

        requestPermission();

        // 读卡器监听
        mReader.setOnStateChangeListener(new Reader.OnStateChangeListener() {
            @Override
            public void onStateChange(int slotNum, int prevState, int currState) {
                if (prevState < Reader.CARD_UNKNOWN
                        || prevState > Reader.CARD_SPECIFIC) {
                    prevState = Reader.CARD_UNKNOWN;
                }
                if (currState < Reader.CARD_UNKNOWN
                        || currState > Reader.CARD_SPECIFIC) {
                    currState = Reader.CARD_UNKNOWN;
                }
                // Create output string
                final String outputString = "Slot " + slotNum + ": "
                        + stateStrings[prevState] + " -> "
                        + stateStrings[currState];
                final int prv = prevState;
                final int cur = currState;
                final int slot = slotNum;
                Log.e("limin","slot = " + slot + ",cur = " + cur + ",prv = " + prv);
                if (slot == 0 && prv == 1 && cur == 2) {
                    powerOn();
                }else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mCxt, "未检测到有卡！", Toast.LENGTH_SHORT).show();
                        }
                    });

                }
            }
        });
    }
    // 启动电源
    private void powerOn() {
        // Set parameters
        PowerParams params = new PowerParams();
        params.slotNum = 0;
        params.action = Reader.CARD_WARM_RESET;
        // 上电
        new PowerTask().execute(params);
    }
    // 设置协议
    private void setProtocol() {
        int preferredProtocols = Reader.PROTOCOL_UNDEFINED;
        preferredProtocols |= Reader.PROTOCOL_T0;
        String preferredProtocolsString = "T=0";
        if (preferredProtocolsString == "") {
            preferredProtocolsString = "None";
        }
        // Set Parameters
        SetProtocolParams params = new SetProtocolParams();
        params.slotNum = 0;
        params.preferredProtocols = preferredProtocols;

        // Set protocol
        printLog("Slot 0 " + ": Setting protocol to "
                + preferredProtocolsString + "...");
        new SetProtocolTask().execute(params);
    }
    private class PowerParams {
        public int slotNum;
        public int action;
    }

    private class PowerResult {
        public byte[] atr;
        public Exception e;
    }

    private class PowerTask extends AsyncTask<PowerParams, Void, PowerResult> {
        @Override
        protected PowerResult doInBackground(PowerParams... params) {
            PowerResult result = new PowerResult();
            try {
                result.atr = mReader.power(params[0].slotNum, params[0].action);
            } catch (Exception e) {
                result.e = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(PowerResult result) {
            if (result.e != null) {
                printLog(result.e.toString());
                showToast();
            } else {
                // Show ATR
                if (result.atr != null) {
                    logBuffer(result.atr, result.atr.length);
                    setProtocol();
                } else {
                    printLog("ATR: None");
                }
            }
        }
    }
    private class OpenTask extends AsyncTask<UsbDevice, Void, Exception> {
        @Override
        protected Exception doInBackground(UsbDevice... params) {
            Exception result = null;
            try {
                mReader.open(params[0]);
            } catch (Exception e) {
                result = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                printLog(result.toString());
                showToast();
            } else {
                printLog("Reader name: " + mReader.getReaderName());
                int numSlots = mReader.getNumSlots();
                printLog("Number of slots: " + numSlots);
            }
        }
    }

    private class CloseTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            mReader.close();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

        }

    }

    private class SetProtocolParams {
        public int slotNum;
        public int preferredProtocols;
    }

    private class SetProtocolResult {
        public int activeProtocol;
        public Exception e;
    }

    private class SetProtocolTask extends
            AsyncTask<SetProtocolParams, Void, SetProtocolResult> {
        @Override
        protected SetProtocolResult doInBackground(SetProtocolParams... params) {
            SetProtocolResult result = new SetProtocolResult();
            try {
                result.activeProtocol = mReader.setProtocol(params[0].slotNum,
                        params[0].preferredProtocols);
            } catch (Exception e) {
                result.e = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(SetProtocolResult result) {
            if (result.e != null) {
                printLog(result.e.toString());
                showToast();
            } else {
                String activeProtocolString = "Active Protocol: ";
                switch (result.activeProtocol) {
                    case Reader.PROTOCOL_T0:
                        activeProtocolString += "T=0";
                        break;
                    case Reader.PROTOCOL_T1:
                        activeProtocolString += "T=1";
                        break;
                    default:
                        activeProtocolString += "Unknown";
                        break;
                }
                // Show active protocol
                printLog(activeProtocolString);
                // 获取cardID
                getCardNo();
            }
        }
    }
    private void getCardNo() {
        String cmd1 = "00A404000E315041592E5359532E4444463031";
        TransmitParams params = new TransmitParams();
        params.slotNum = 0;
        params.controlCode = -1;
        params.commandString = cmd1;
        String s1 = doInBackground(params);
        Log.e("response1", s1);

        String cmd2 = "00B2010C00";
        Log.e("send2", cmd2);
        TransmitParams params1 = new TransmitParams();
        params1.slotNum = 0;
        params1.controlCode = -1;
        params1.commandString = cmd2;
        String s2 = doInBackground(params1);
        Log.e("response2", s2);

        String cmd3 = "00A4040008A000000333010101";
        Log.e("send3", cmd3);
        TransmitParams params2 = new TransmitParams();
        params2.slotNum = 0;
        params2.controlCode = -1;
        params2.commandString = cmd3;
        String s3 = doInBackground(params2);
        Log.e("response3", s3);

        String cmd4 = "80A800000b8309010000000004000156";
        Log.e("send4", cmd4);
        TransmitParams params3 = new TransmitParams();
        params3.slotNum = 0;
        params3.controlCode = -1;
        params3.commandString = cmd4;
        String s4 = doInBackground(params3);
        Log.e("response4", s4);

        String cmd5 = "00B2010C00";
        Log.e("send5", cmd5);
        TransmitParams params4 = new TransmitParams();
        params4.slotNum = 0;
        params4.controlCode = -1;
        params4.commandString = cmd5;
        String s5 = doInBackground(params4);
        Log.e("response5", s5);

        String find1 = "57 13";
        String find2 = "57 12";
        String find3 = "57 11";
        String find4 = "57 10";
        String find5 = "57 ";
        String end = "D";
        String cardno = "";
        if (s5.indexOf(find1) != -1) {
            cardno = s5.substring(s5.indexOf(find1) + 5, s5.indexOf(end));
        } else if (s5.indexOf(find2) != -1) {
            cardno = s5.substring(s5.indexOf(find1) + 5, s5.indexOf(end));
        } else if (s5.indexOf(find3) != -1) {
            cardno = s5.substring(s5.indexOf(find1) + 5, s5.indexOf(end));
        } else if (s5.indexOf(find4) != -1) {
            cardno = s5.substring(s5.indexOf(find1) + 5, s5.indexOf(end));
        } else if (s5.indexOf(find5) != -1) {
            cardno = s5.substring(s5.indexOf(find1) + 3, s5.indexOf(end));
        }
        //返回银行卡号
        Toast.makeText(mCxt, "银行卡号：" + cardno, Toast.LENGTH_SHORT).show();
    }

    private class TransmitParams {
        public int slotNum;
        public int controlCode;
        public String commandString;
    }
    private class TransmitProgress {
        public int controlCode;
        public byte[] command;
        public int commandLength;
        public byte[] response;
        public int responseLength;
        public Exception e;
    }

    public String doInBackground(TransmitParams params) {
        String str = "";
        TransmitProgress progress = null;
        byte[] command = null;
        byte[] response = null;
        int responseLength = 0;
        int foundIndex = 0;
        int startIndex = 0;

        do {
            foundIndex = params.commandString.indexOf('\n', startIndex);//
            if (foundIndex >= 0) {
                command = toByteArray(params.commandString.substring(
                        startIndex, foundIndex));
            } else {
                command = toByteArray(params.commandString
                        .substring(startIndex));
            }
            startIndex = foundIndex + 1;
            response = new byte[300];
            progress = new TransmitProgress();
            progress.controlCode = params.controlCode;
            try {

                if (params.controlCode < 0) {
                    // Transmit APDU
                    responseLength = mReader.transmit(params.slotNum,
                            command, command.length, response,
                            response.length);

                } else {
                    // Transmit control command
                    responseLength = mReader.control(params.slotNum,
                            params.controlCode, command, command.length,
                            response, response.length);
                }
                progress.command = command;
                progress.commandLength = command.length;
                progress.response = response;
                progress.responseLength = responseLength;
                progress.e = null;
            } catch (Exception e) {
                progress.command = null;
                progress.commandLength = 0;
                progress.response = null;
                progress.responseLength = 0;
                progress.e = e;
            }
        } while (foundIndex >= 0);

        if (progress.e != null) {
            showToast();
        } else {
            str = toHexString(progress.response);
        }
        return str;
    }

    /**
     * Converts the integer to HEX string.
     *
     * @param i
     *            the integer.
     * @return the HEX string.
     */
    private String toHexString(int i) {
        String hexString = Integer.toHexString(i);
        if (hexString.length() % 2 != 0) {
            hexString = "0" + hexString;
        }
        return hexString.toUpperCase();
    }

    /**
     * Converts the byte array to HEX string.
     * @param buffer
     *            the buffer.
     * @return the HEX string.
     */
    private String toHexString(byte[] buffer) {
        String bufferString = "";
        for (int i = 0; i < buffer.length; i++) {
            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }
            bufferString += hexChar.toUpperCase() + " ";
        }
        return bufferString;
    }

    /**
     * Converts the HEX string to byte array.
     * @param hexString
     *            the HEX string.
     * @return the byte array.
     */
    private byte[] toByteArray(String hexString) {
        int hexStringLength = hexString.length();
        byte[] byteArray = null;
        int count = 0;
        char c;
        int i;
        // Count number of hex characters
        for (i = 0; i < hexStringLength; i++) {
            c = hexString.charAt(i);
            if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f') {
                count++;
            }
        }
        byteArray = new byte[(count + 1) / 2];
        boolean first = true;
        int len = 0;
        int value;
        for (i = 0; i < hexStringLength; i++) {
            c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }
            if (value >= 0) {
                if (first) {
                    byteArray[len] = (byte) (value << 4);
                } else {
                    byteArray[len] |= value;
                    len++;
                }
                first = !first;
            }
        }
        return byteArray;
    }

    // 接收系统广播
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @SuppressLint("NewApi")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            printLog("action: " + action + "...");

            if (ACTION_USB_PERMISSION.equals(action)) {

                synchronized (this) {

                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        if (device != null) {
                            // 启动读卡器
                            printLog("Opening reader: "
                                    + device.getDeviceName() + "...");
                            new OpenTask().execute(device);
                        }
                    } else {
                        printLog("Permission denied for device "
                                + device.getDeviceName());
                        showToast();
                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                synchronized (this) {

                    // Update reader list
                    mReaderAdapter.clear();
                    for (UsbDevice device : mManager.getDeviceList().values()) {
                        if (mReader.isSupported(device)) {
                            mReaderAdapter.add(device.getDeviceName());
                        }
                    }
                    UsbDevice device = (UsbDevice) intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && device.equals(mReader.getDevice())) {
                        // Clear slot items
                        mSlotAdapter.clear();
                        // Close reader
                        printLog("Closing reader...");
                        // new CloseTask().execute();
                    }
                }
            }
        }
    };

    // 错误提示
    private void showToast() {
        Toast.makeText(mCxt, "设备错误，请重试！", Toast.LENGTH_SHORT).show();
    }

    // 错误打印
    private void printLog(String log) {
        Log.e(TAG, log);
    }

    // buffer打印
    private void logBuffer(byte[] buffer, int bufferLength) {
        String bufferString = "";
        for (int i = 0; i < bufferLength; i++) {
            String hexChar = Integer.toHexString(buffer[i] & 0xFF);
            if (hexChar.length() == 1) {
                hexChar = "0" + hexChar;
            }
            if (i % 16 == 0) {
                if (bufferString != "") {
                    printLog(bufferString);
                    bufferString = "";
                }
            }
            bufferString += hexChar.toUpperCase() + " ";
        }
        if (bufferString != "") {
            printLog(bufferString);
        }
    }
}
