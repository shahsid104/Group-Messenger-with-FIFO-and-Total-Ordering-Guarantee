package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    int  flag = 0 , objectCount = 0, checkerFlag = 0;
    double proposedSequenceNumber = 0;
    boolean failureDetected = false;
    List<messageObject> holdBackQueue = Collections.synchronizedList(new ArrayList<messageObject>());
    List<Boolean> activePort = Collections.synchronizedList(new ArrayList<Boolean>());
    static final Object synchronizer = new Object();
    HashMap<String,Integer> portMapping = new HashMap<>();
    int contentProviderSequenceNumber = 0;
    static final int SERVER_PORT = 10000;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    private void fillHashMap()
    {
        portMapping.put(REMOTE_PORT0,0);
        activePort.add(0,true);
        portMapping.put(REMOTE_PORT1,1);
        activePort.add(1,true);
        portMapping.put(REMOTE_PORT2,2);
        activePort.add(2,true);
        portMapping.put(REMOTE_PORT3,3);
        activePort.add(3,true);
        portMapping.put(REMOTE_PORT4,4);
        activePort.add(4,true);

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        fillHashMap();
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        final EditText userInputField = (EditText)findViewById(R.id.editText1);
        final Button sendButton = (Button)findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = userInputField.getText().toString();
                userInputField.setText(""); // This is one way to reset the input box.
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(10000);
                    ObjectInputStream msgReceived = new ObjectInputStream(clientSocket.getInputStream());
                    messageObject receivedObject = (messageObject) msgReceived.readObject();
                    synchronized (activePort) {
                        if (!activePort.get(receivedObject.process_id))
                            continue;
                    }

                    if (receivedObject.flag == 0) {
                        ObjectOutputStream msgToSend = new ObjectOutputStream(clientSocket.getOutputStream());
                        synchronized (synchronizer) {
                            receivedObject.finalSequenceNumber = proposedSequenceNumber;
                            proposedSequenceNumber += Math.random() + 3;
                        }
                        Log.d("Process ID phase 1", String.valueOf(receivedObject.process_id));
                        msgToSend.writeObject(receivedObject);
                        synchronized (holdBackQueue) {
                            holdBackQueue.add(receivedObject);
                        }
                    } else if (receivedObject.flag == 1) {
                        Log.d("Final Sequence Number", String.valueOf(receivedObject.finalSequenceNumber));
                        Log.d("Message", String.valueOf(receivedObject.message));
                        Log.d("Process ID", String.valueOf(receivedObject.process_id));
                        Log.d("Hold back queue size",String.valueOf(holdBackQueue.size()));
                        Log.d("Hold back queue size",String.valueOf(receivedObject.flag));
                        synchronized (synchronizer) {
                            if (receivedObject.finalSequenceNumber >= proposedSequenceNumber)
                                proposedSequenceNumber = receivedObject.finalSequenceNumber + 1;
                        }

                        synchronized (holdBackQueue) {
                            Log.d("Lock acquire","server");
                            for (int i = 0; i < holdBackQueue.size(); i++) {
                                    if (holdBackQueue.get(i).objectID.compareTo(receivedObject.objectID) == 0 && holdBackQueue.get(i).message.compareTo(receivedObject.message) == 0) {
                                        holdBackQueue.get(i).flag = 1;
                                        holdBackQueue.get(i).finalSequenceNumber = receivedObject.finalSequenceNumber;
                                        holdBackQueue.get(i).sequenceNumber = receivedObject.finalSequenceNumber;
                                        Log.d("Element changed",String.valueOf(holdBackQueue.get(i).message));
                                        Log.d("Element changed",String.valueOf(holdBackQueue.get(i).flag));
                                        Log.d("Element add again",String.valueOf(holdBackQueue.size()));
                                        break;
                                    }
                                }

                            Collections.sort(holdBackQueue);
                            int index = 0;
                            ListIterator<messageObject> it = holdBackQueue.listIterator();
                                while (it.hasNext()) {
                                    messageObject obj = it.next();
                                    if (!activePort.get(obj.process_id)) {
                                        it.remove();
                                        continue;
                                    }
                                    else if (obj.flag == 1) {
                                        ContentValues cv = new ContentValues();
                                        cv.put(KEY_FIELD, String.valueOf(contentProviderSequenceNumber++));
                                        cv.put(VALUE_FIELD, obj.message);
                                        Log.d("CV", cv.toString());
                                        Log.d("PQ", String.valueOf(obj.finalSequenceNumber));
                                        ContentResolver mContentResolver = getContentResolver();
                                        mContentResolver.insert(mUri, cv);
                                        it.remove();
                                        Log.d("Hold backs queue size", String.valueOf(holdBackQueue.size()));
                                    } else {
                                        Log.d("Not suitable to add",String.valueOf(obj.process_id));
                                        Log.d("Not suitable to add",String.valueOf(obj.objectID));
                                        Log.d("Not suitable to add",obj.message);
                                        Log.d("Not suitable to add",String.valueOf(obj.flag));
                                        break;
                                    }
                                }
                        }
                        Log.d("Lock release","server");
                        clientSocket.close();
                    }
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }catch(ClassNotFoundException e){
                    e.printStackTrace();
                }

            }
        }

        protected void onProgressUpdate(String...strings) {
            String strReceived = strings[0].trim();
            TextView displayTextView = (TextView) findViewById(R.id.textView1);
            displayTextView.append(strReceived + "\t\n");
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            Socket socket = new Socket();
            try {
                String port = msgs[1];
                messageObject msg = new messageObject();
                msg.message = msgs[0];
                msg.flag = 0;
                msg.sequenceNumber = -1;
                msg.process_id = portMapping.get(port);
                msg.objectID = String.valueOf(msg.process_id) + String.valueOf(objectCount);
                Log.d("msg to send", msg.message);
                double remotePort0SequenceNumber = 0, remotePort1SequenceNumber = 0, remotePort2SequenceNumber = 0, remotePort3SequenceNumber = 0, remotePort4SequenceNumber = 0;
                try {
                        if (activePort.get(0)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT0));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                            ObjectInputStream msgToReceieve = new ObjectInputStream(socket.getInputStream());
                            messageObject receivedObject = (messageObject) msgToReceieve.readObject();
                            remotePort0SequenceNumber = receivedObject.finalSequenceNumber;
                            socket.close();
                            Log.d("Remote Port 0", String.valueOf(remotePort0SequenceNumber));
                        }
                } catch (SocketTimeoutException e) {
                        setActivePortValue(0);
                    Log.d("Failure Detected", "0");
                    removeStalledMessages();
                }catch(IOException e){
                    setActivePortValue(0);
                    Log.d("Failure Detected IO", "0");
                    removeStalledMessages();
                }catch(Exception e){
                    e.printStackTrace();
                }

                try {
                        if (activePort.get(1)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT1));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                            ObjectInputStream msgToReceieve = new ObjectInputStream(socket.getInputStream());
                            messageObject receivedObject = (messageObject) msgToReceieve.readObject();
                            remotePort1SequenceNumber = receivedObject.finalSequenceNumber;
                            socket.close();
                            Log.d("Remote Port 1", String.valueOf(remotePort1SequenceNumber));
                        }
                } catch (SocketTimeoutException e) {
                    setActivePortValue(1);
                    Log.d("Failure Detected", "1");
                    removeStalledMessages();
                }catch(IOException e){
                    setActivePortValue(1);
                    Log.d("Failure Detected IO", "1");
                    removeStalledMessages();
                }catch (Exception e){
                    e.printStackTrace();
                }

                try {
                        if (activePort.get(2)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT2));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                            ObjectInputStream msgToReceieve = new ObjectInputStream(socket.getInputStream());
                            messageObject receivedObject = (messageObject) msgToReceieve.readObject();
                            remotePort2SequenceNumber = receivedObject.finalSequenceNumber;
                            socket.close();
                            Log.d("Remote Port 2", String.valueOf(remotePort2SequenceNumber));
                        }
                } catch (SocketTimeoutException e) {
                    setActivePortValue(2);
                    Log.d("Failure Detected", "2");
                    removeStalledMessages();
                }catch(IOException e){
                    setActivePortValue(2);
                    Log.d("Failure Detected IO", "2");
                    removeStalledMessages();
                }
                catch (Exception e){
                    e.printStackTrace();
                }
                try {
                        if (activePort.get(3)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT3));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                            ObjectInputStream msgToReceieve = new ObjectInputStream(socket.getInputStream());
                            messageObject receivedObject = (messageObject) msgToReceieve.readObject();
                            remotePort3SequenceNumber = receivedObject.finalSequenceNumber;
                            socket.close();
                            Log.d("Remote Port 3", String.valueOf(remotePort3SequenceNumber));
                        }
                } catch (SocketTimeoutException e) {
                    setActivePortValue(3);
                    Log.d("Failure Detected", "3");
                    removeStalledMessages();
                }catch(IOException e){
                    setActivePortValue(3);
                    Log.d("Failure Detected IO", "3");
                    removeStalledMessages();
                }catch (Exception e)
                {
                    e.printStackTrace();
                }
                try{
                        if (activePort.get(4)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT4));
                            socket.setSoTimeout(3000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                            ObjectInputStream msgToReceieve = new ObjectInputStream(socket.getInputStream());
                            messageObject receivedObject = (messageObject) msgToReceieve.readObject();
                            remotePort4SequenceNumber = receivedObject.finalSequenceNumber;
                            socket.close();
                            Log.d("Remote Port 4", String.valueOf(remotePort4SequenceNumber));
                        }
                }catch (SocketTimeoutException e) {
                    setActivePortValue(4);
                        Log.d("Failure Detected", "4");
                        removeStalledMessages();
                }catch(IOException e){
                    setActivePortValue(4);
                    Log.d("Failure Detected","IOException");
                    removeStalledMessages();

                } catch (Exception e){
                    e.printStackTrace();
                }
                synchronized (synchronizer) {
                    double finalSequenceNumber = Math.max(++proposedSequenceNumber, Math.max(remotePort0SequenceNumber, Math.max(remotePort4SequenceNumber, Math.max(remotePort3SequenceNumber, Math.max(remotePort1SequenceNumber, remotePort2SequenceNumber)))));
                    Log.d("Final Sequence", "decided");
                    proposedSequenceNumber += Math.random() + 5;
                    msg.sequenceNumber = finalSequenceNumber;
                    msg.flag = 1;
                    msg.finalSequenceNumber = finalSequenceNumber;
                }
                try {
                        if (activePort.get(0)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT0));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                        }
                }catch(SocketTimeoutException e){
                    setActivePortValue(0);
                    Log.d("Timeout","0");
                    removeStalledMessages();
                }catch (IOException e){
                    Log.d("IO","0");
                    setActivePortValue(0);
                    removeStalledMessages();
                }
                try {
                        if (activePort.get(1)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT1));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                        }
                }catch(SocketTimeoutException e){
                    Log.d("Timeout","1");
                    setActivePortValue(1);
                    removeStalledMessages();
                }catch (IOException e){
                    Log.d("IO","1");
                    setActivePortValue(1);
                    removeStalledMessages();
                }
                try {
                        if (activePort.get(2)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT2));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                        }
                }catch(SocketTimeoutException e){
                    Log.d("Timeout","2");
                    setActivePortValue(2);
                    removeStalledMessages();
                }catch (IOException e){
                    Log.d("IO","2");
                    setActivePortValue(2);
                    removeStalledMessages();
                }

                try {
                        if (activePort.get(3)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT3));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                        }
                }catch(SocketTimeoutException e){
                    Log.d("Timeout","0");
                    setActivePortValue(3);
                    removeStalledMessages();
                }catch (IOException e){
                    Log.d("IO","0");
                    setActivePortValue(3);
                    removeStalledMessages();
                }

                try {
                        if (activePort.get(4)) {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(REMOTE_PORT4));
                            socket.setSoTimeout(10000);
                            ObjectOutputStream msgToSend = new ObjectOutputStream(socket.getOutputStream());
                            msgToSend.writeObject(msg);
                        }
                }catch(SocketTimeoutException e){
                    Log.d("Timeout","0");
                    setActivePortValue(4);
                    removeStalledMessages();
                }catch (IOException e){
                    Log.d("IO","0");
                    setActivePortValue(4);
                    removeStalledMessages();
                }

            } catch (Exception e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            return null;
        }
    }

    public void setActivePortValue(int port)
    {
        synchronized(activePort){
            activePort.add(port,false);
        }

    }
    public void removeStalledMessages(){
        Log.d("Removing message","stalled");
        synchronized (holdBackQueue) {
            Log.d("Lock Acquired","stall");
            ListIterator<messageObject> checker = holdBackQueue.listIterator();
                while (checker.hasNext()) {
                    Log.d("iterating message", "stalled");
                    messageObject msg = checker.next();
                        Log.d("Active port", String.valueOf(activePort.get(msg.process_id)));
                        if (!activePort.get(msg.process_id)) {
                            Log.d("Removing message", msg.message);
                            Log.d("Process id", String.valueOf(msg.process_id));
                            checker.remove();
                        }
                }
        }
        Log.d("Lock release","stall");
    }
}
