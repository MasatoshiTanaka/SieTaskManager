package jp.tdu.ics.masatoshi.sietaskmanager.app;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.util.*;


public class MainActivity extends Activity {
    private String taskText;
    private ListView taskListView;
    private ArrayAdapter<String> taskListAdapter;
    private EditText taskEditText;
    Toast toast;
    SQLiteDatabase sqLiteDatabase;
    MySQLiteOpenHelper mySQLiteOpenHelper;
    Socket socket = null;

    Map<String, List<String>> map = new TreeMap<>();

    private int counter = 1;
    IntentFilter intentFilter;
    WifiManager wifiManager;
    WiFiReceiver wifiReceiver;
    final String localUrl = "http://192.168.11.9:32156/IndoorPositioning/wifi/test";
    final String globalUrl ="http://133.20.243.197:32156/IndoorPositioning/wifi/test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toast = Toast.makeText(getApplicationContext(), null, Toast.LENGTH_SHORT);

        Button addButton = (Button)findViewById(R.id.addButton);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addButtonAction();
            }
        });


        taskEditText = (EditText)findViewById(R.id.taskEditText);

        taskListAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1);
        taskListView = (ListView)findViewById(R.id.taskListView);



        mySQLiteOpenHelper = new MySQLiteOpenHelper(this);
        sqLiteDatabase = mySQLiteOpenHelper.getReadableDatabase();
        Cursor cursor = sqLiteDatabase.query("taskTable", new String[]{"task"}, null, null, null, null, null);
        boolean next = cursor.moveToFirst();
        while(next){
            taskListAdapter.add(cursor.getString(0) + "\n" );
            next = cursor.moveToNext();
        }
        cursor.close();
        taskListView.setAdapter(taskListAdapter);


        taskListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ListView list = (ListView) parent;
                String selectedItem = (String) list.getItemAtPosition(position);
                deleteTask(selectedItem);
                return false;
            }
        });




        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        wifiManager.startScan();
    }

    private void deleteTask(String selectedItem){
        taskListAdapter.remove(selectedItem);
        sqLiteDatabase.delete("taskTable", null , null);
    }

    private void addButtonAction(){
        taskText = taskEditText.getText().toString();
        taskListAdapter.add(taskText + "\n");


        taskEditText.getText().clear();

        String placeID = getPlaceID(taskText);
        sqLiteDatabase = mySQLiteOpenHelper.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        if(!taskText.isEmpty()) {
            contentValues.put("task", taskText);
            contentValues.put("placeID", placeID);
        }
        sqLiteDatabase.insert("taskTable", null, contentValues);
    }



    public void  postWiFiData(final String url, final String wifiJsonData){
        new AsyncTask<Void, Void, String>() {

            int statusCode;
            @Override
            protected String doInBackground(Void... params) {
                HttpResponse httpResponse = null;
                String resultMessage = "";
                try {
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpPost httpPost = new HttpPost(url);
                    StringEntity stringEntity = new StringEntity(wifiJsonData);
                    stringEntity.setContentType("application/json");
                    httpPost.setEntity(stringEntity);
                    httpResponse = httpClient.execute(httpPost);
                    resultMessage = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
                    statusCode = httpResponse.getStatusLine().getStatusCode();
                } catch (IOException e) {
                }
                return resultMessage;
            }

            @Override
            protected void onPostExecute(String resultMessage){
                if(statusCode == HttpStatus.SC_OK) {
                    Gson gson = new Gson();
                    String[] resultList = gson.fromJson(resultMessage, String[].class);
                    List<String> result = Arrays.asList(resultList);

                    mySQLiteOpenHelper = new MySQLiteOpenHelper(getApplicationContext());
                    sqLiteDatabase = mySQLiteOpenHelper.getReadableDatabase();
                    Cursor cursor = sqLiteDatabase.query("taskTable", new String[]{"task", "placeID"}, null, null, null, null, null);
                    boolean next = cursor.moveToFirst();
                    StringBuilder toastMessage = new StringBuilder();
                    if (result != null) {
                        if (result.isEmpty()) {
                            toastMessage.append("推定できませんでした");
                        } else {
                            while (next) {
                                if (result.get(0).equals(cursor.getString(1))) {
                                    toastMessage.append(result.get(0) + "付近にいます。" + "\n" +
                                            "タスク名: " + "{" + cursor.getString(0) + "}" + "\n"
                                            + "が解決できます。" + "\n");
                                }
                                next = cursor.moveToNext();
                            }
                            toastMessage.append(result);
                        }
                        toast.setText(toastMessage);
                        toast.show();

                        cursor.close();
                        wifiManager.startScan();
                    }
                }

            }

        }.execute();
    }

    class WiFiReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Gson gson = new Gson();
            List<ScanResult> scanResultList = wifiManager.getScanResults();
            for (ScanResult scanResult : scanResultList) {
                String bssid = scanResult.BSSID;
                int rssi = scanResult.level;
                if(!map.containsKey(bssid)){
                    List<String> list = new ArrayList<>();
                    list.add(String.valueOf(rssi));
                    map.put(bssid, list);
                }else{
                    map.get(bssid).add(String.valueOf(rssi));
                }
            }
            if(counter == 1){
                String wifiJsonData = gson.toJson(map);
                if(wifiManager.getConnectionInfo().getSSID().equals("\"TDN SERA\"")) {
                    postWiFiData(localUrl, wifiJsonData);
                    System.out.println("local");
                }else{
                    postWiFiData(globalUrl, wifiJsonData);
                    System.out.println("global");
                }
                map.clear();
            }
            wifiManager.startScan();
            counter = 1;
        }
    }

    public String getPlaceID(String task){
        String placeID;
        if(task.matches(".*入金する*.")){
            placeID = "ATM";
        }else if(task.matches(".*振り込む*.")){
            placeID = "ATM";
        }else if(task.matches(".*引き落とす*.")){
            placeID = "ATM";
        }else if(task.matches(".*買う*.")){
            placeID = "";
        }else if(task.matches(".*購入する*.")){
            placeID = "生協";
        }else if(task.matches(".*提出する*.")){
            placeID = "事務部";
        }else if(task.matches(".*聞く*.")){
            placeID = "事務部";
        }else if(task.matches(".*発券する*.")){
            placeID = "発券機";
        }else if(task.matches(".*発行する*.")){
            placeID = "発行する";
        }else if(task.matches(".*研究する*.")){
            placeID = "研究棟412";
        }
        else{
            placeID = null;
        }
        return placeID;
    }

    @Override
    public void onResume(){
        super.onResume();
        intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiReceiver = new WiFiReceiver();
        registerReceiver(wifiReceiver, intentFilter);
    }
    @Override
    public void onPause(){
        super.onPause();
        unregisterReceiver(wifiReceiver);
        if(socket != null) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

