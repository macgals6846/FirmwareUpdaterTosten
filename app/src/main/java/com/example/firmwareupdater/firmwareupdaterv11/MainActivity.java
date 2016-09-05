package com.example.firmwareupdater.firmwareupdaterv11;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;

import static android.R.attr.data;

public class MainActivity extends AppCompatActivity {
    private final int REQUEST_CODE_PICK_DIR = 1;
    private final int REQUEST_CODE_PICK_FILE = 2;
    TextView edittext,displaytext,textView_server,textView_client;
    ScrollView chat_ScrollView,scroll_server,scroll_client;
    private String mPath = "adventur.txt";
    private List<String> mLines;
    ServerSocket serverSocket;
    String message, message_from_client = null,serverWrite = null ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        edittext = (TextView)findViewById(R.id.display_directory);
        textView_server = (TextView) findViewById(R.id.textView_server);
        textView_client = (TextView) findViewById(R.id.textView_client);
        chat_ScrollView = (ScrollView)findViewById(R.id.chat_ScrollView);
        scroll_server = (ScrollView)findViewById(R.id.scroll_server);
        scroll_client = (ScrollView)findViewById(R.id.scroll_client);
        displaytext = (TextView)findViewById(R.id.display_text);

        final Activity activityForButton = this;

        final Button startBrowser4FileButton = (Button) findViewById(R.id.button_browse);
        startBrowser4FileButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent fileExploreIntent = new Intent(
                        com.example.firmwareupdater.firmwareupdaterv11.FileBrowserActivity.INTENT_ACTION_SELECT_FILE,
                        null, activityForButton, com.example.firmwareupdater.firmwareupdaterv11.FileBrowserActivity.class
                );
                //        		fileExploreIntent.putExtra(
                //        				ua.com.vassiliev.androidfilebrowser.FileBrowserActivity.startDirectoryParameter,
                //        				"/sdcard"
                //        				);
                startActivityForResult(fileExploreIntent, REQUEST_CODE_PICK_FILE);
            }//public void onClick(View v) {
        });


    }

    public void UpdateFirmware(View view) {
        if(mPath.endsWith(".hex")) {
            File file = new File(mPath);
            String[] loadText = Load(file);
            String finalString = "";
            for (int i = 0; i < loadText.length; i++) {
                finalString += loadText[i] + System.getProperty("line.separator");
                Integer.parseInt(loadText[i].substring(1,3), 16);
                Integer.parseInt(loadText[i].substring(3,7), 16);
                Integer.parseInt(loadText[i].substring(7,9), 16);

            }
            displaytext.setText("Directory:\n"+mPath+"\n"+"File:\n"+finalString);
        }
        else{
            displaytext.setText("Cannot Load Current File.\nSelect file with .hex extension");
        }

        chat_ScrollView.post(new Runnable()
        {
            public void run()
            {
                chat_ScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void StartServer(View view) {
        textView_server.setText(getIpAddress());

        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();

        scroll_server.post(new Runnable()
        {
            public void run()
            {
                scroll_server.fullScroll(View.FOCUS_DOWN);
            }
        });
        scroll_client.post(new Runnable()
        {
            public void run()
            {
                scroll_client.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 8080;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SocketServerPORT);

                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    message = socket.getInetAddress()+":"+socket.getPort()+"\n";

                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView_client.setText(message);
                        }
                    });

                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket);
                    socketServerReplyThread.start();

                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private class SocketServerReplyThread extends Thread {

        private Socket clientThreadSocket;
        private BufferedReader client_input;
        private PrintWriter server_output;

        SocketServerReplyThread(Socket socket) {
            this.clientThreadSocket = socket;
        }

        @Override
        public void run() {

            while(!Thread.currentThread().isInterrupted()){
                try {
                    this.client_input = new BufferedReader(new InputStreamReader(this.clientThreadSocket.getInputStream()));
                    String read = client_input.readLine();
                    message_from_client = "Arduino Wifi:" + read + "\n";

                    server_output = new PrintWriter(clientThreadSocket.getOutputStream(), true);
                    String server_msgReply = null;
                    switch(read){
                        case "Hello":
                            server_msgReply = "Welcome";
                            break;
                        case "Hex":
                            //TODO Read hex file from specified path and filename
                            server_msgReply = "Ok";
                            break;
                        case "x00":
                            //TODO Programming using STK500 Protocol
                            //server_msgReply = "Welcome";
                            break;
                        default:
                            break;

                    }
                    serverWrite = "Android Phone:" + server_msgReply + "\n";
                    server_output.print(server_msgReply);
                    server_output.flush();

                    if(read != null){
                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                displaytext.append(message_from_client);
                                displaytext.append(serverWrite);
                            }
                        });
                    }
                    //clientThreadSocket.close();

                    message = "Server message replayed:" + server_msgReply;

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    message += "Something wrong! " + e.toString() + "\n";
                }
            }

            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    displaytext.append(message);
                }
            });
        }

    }



    public static String[] Load(File file)
    {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(file);
        }
        catch (FileNotFoundException e) {e.printStackTrace();}
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader br = new BufferedReader(isr);

        String test;
        int anzahl=0;
        try
        {
            while ((test=br.readLine()) != null)
            {
                anzahl++;
            }
        }
        catch (IOException e) {e.printStackTrace();}

        try
        {
            fis.getChannel().position(0);
        }
        catch (IOException e) {e.printStackTrace();}

        String[] array = new String[anzahl];

        String line;
        int i = 0;
        try
        {
            while((line=br.readLine())!=null)
            {
                array[i] = line;
                i++;
            }
        }
        catch (IOException e) {e.printStackTrace();}
        return array;
    }



    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data){
        if (requestCode == REQUEST_CODE_PICK_FILE) {
            if (resultCode == RESULT_OK) {
                mPath = data.getStringExtra(com.example.firmwareupdater.firmwareupdaterv11.FileBrowserActivity.returnFileParameter);
                edittext.setText(mPath);

            } else {//if(resultCode == this.RESULT_OK) {
                edittext.setText("Received NO result from file browser");
            }//END } else {//if(resultCode == this.RESULT_OK) {
        }//if (requestCode == REQUEST_CODE_PICK_FILE) {

        super.onActivityResult(requestCode, resultCode, data);
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += inetAddress.getHostAddress() + ": 8080";
                        if(enumInetAddress.hasMoreElements()){
                            ip += "\n";
                        }
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

}
