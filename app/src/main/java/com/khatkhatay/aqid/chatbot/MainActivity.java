package com.khatkhatay.aqid.chatbot;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.JsonElement;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.AIServiceException;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    ListView conversationList;
    ImageButton sendButton;
    EditText userInput;
    AIConfiguration aiConfiguration;
    AIDataService aiDataService;

    public static ArrayList<ChatData> chatlist;
    public static ChatAdapter chatAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final AIConfiguration.SupportedLanguages lang =
                AIConfiguration.SupportedLanguages.fromLanguageTag("en");


        aiConfiguration = new AIConfiguration("b98966f20c1148f4979894968187b4b7",lang);
        aiDataService = new AIDataService(aiConfiguration);

        sendButton = (ImageButton)findViewById(R.id.imageButton);
        userInput = (EditText)findViewById(R.id.inputMessage);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String text = userInput.getText().toString();
                if(!text.trim().equals("")) {
                    addMessage(text,true);
                    userInput.setText("");
                    sendRequest(text);
                }
            }
        });


        conversationList = (ListView) findViewById(R.id.ConversationList);
        conversationList.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        conversationList.setStackFromBottom(true);

        chatlist = new ArrayList<ChatData>();
        chatAdapter = new ChatAdapter(this, chatlist);
        conversationList.setAdapter(chatAdapter);


    }

    private void addMessage(String message, boolean isMine) {

        final ChatData chatMessage = new ChatData("", "","", isMine);
        chatMessage.body = message;
        chatAdapter.add(chatMessage);
        chatAdapter.notifyDataSetChanged();
    }

    private void sendRequest(String userText) {

        final String contextString = String.valueOf(userText);

        final AsyncTask<String, Void, AIResponse> task = new AsyncTask<String, Void, AIResponse>() {

            private AIError aiError;

            @Override
            protected AIResponse doInBackground(final String... params) {
                final AIRequest request = new AIRequest();
                String query = params[0];

                if (!TextUtils.isEmpty(query))
                    request.setQuery(query);

                try {
                    return aiDataService.request(request);
                } catch (final AIServiceException e) {
                    aiError = new AIError(e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final AIResponse response) {
                if (response != null) {
                    onResult(response);
                } else {
                    onError(aiError);
                }
            }
        };

        task.execute(contextString);
    }

    private void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onResult");

                Log.i(TAG, "Received success response");

                // this is example how to get different parts of result object
                final Status status = response.getStatus();
                Log.i(TAG, "Status code: " + status.getCode());
                Log.i(TAG, "Status type: " + status.getErrorType());

                final Result result = response.getResult();
                Log.i(TAG, "Resolved query: " + result.getResolvedQuery());

                Log.i(TAG, "Action: " + result.getAction());

                final String speech = result.getFulfillment().getSpeech();
                Log.i(TAG, "Speech: " + speech);

                if (!TextUtils.isEmpty(speech))
                    addMessage(speech,false);

                final Metadata metadata = result.getMetadata();
                if (metadata != null) {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

                final HashMap<String, JsonElement> params = result.getParameters();
                if (params != null && !params.isEmpty()) {
                    Log.i(TAG, "Parameters: ");
                    for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                        Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
                    }
                }
                switch (metadata.getIntentName()){
                    case "Weather":
                        getWeatherData(params);

                        break;
                }
            }

        });
    }

    private void parseData(JSONObject json){
        try {
            if(json.getJSONObject("query").getInt("count") > 0){

                String details = "";

                JSONObject channel = json.getJSONObject("query").getJSONObject("results").getJSONObject("channel");

                details = "Weather looks " + channel.getJSONObject("item").getJSONObject("condition").getString("text") +
                        " in " + channel.getString("title").replace("Yahoo! Weather - ","");

                details = details + "\nTemprature is " + channel.getJSONObject("item").getJSONObject("condition").getString("temp")
                        + (char) 0x00B0 + "F.";


                addMessage(details,false);

            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void getWeatherData(HashMap<String, JsonElement> params) {

        String geoCity = "";
        if (params != null && !params.isEmpty()) {
            Log.i(TAG, "Parameters: ");
            for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
                if(entry.getKey().equals("geo-city"))
                    geoCity = entry.getValue().getAsString();
            }
        }

        final AsyncTask<String, Void, JSONObject> task = new AsyncTask<String, Void, JSONObject>() {

            private AIError aiError;
            JSONObject weatherData = null;

            @Override
            protected JSONObject doInBackground(final String... params) {
                final AIRequest request = new AIRequest();
                String query = params[0];

                if (!TextUtils.isEmpty(query)) {
                    try {
                        URL url = new URL("https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20weather.forecast%20where%20woeid%20in%20(select%20woeid%20from%20geo.places(1)%20where%20text%3D%22" + query + "%22)&format=json&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys"); //Enter URL here
                        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.connect();

                        InputStream in = new BufferedInputStream(httpURLConnection.getInputStream());
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        String line;
                        String sb = "";
                        while ((line = reader.readLine()) != null) {
                            sb = sb + line;
                        }

                        JSONObject myJsonArray = new JSONObject(sb);

                        //JSONObject jsonObj = new JSONObject(sb);

                        weatherData = myJsonArray;
                    } catch (final Exception e) {
                        return null;
                    }
                }
                return weatherData;
            }

            @Override
            protected void onPostExecute(final JSONObject response) {
                if (response != null) {
                    parseData(response);
                } else {
                }
            }
        };

        task.execute(geoCity);
    }

    private void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addMessage(error.toString(),false);
            }
        });
    }
}