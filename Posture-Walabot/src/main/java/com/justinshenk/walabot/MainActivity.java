package com.justinshenk.walabot;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.support.design.button.MaterialButton;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    TextView currDistance;
    TextView calibration;
    TextView sensitivityLabel;
    int nrDataPoints = 60;
    int notifyCountdown = 20;
    Double minDistance = 0.;
    Double[] dataset = new Double[nrDataPoints];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // set a change listener on the SeekBar
        SeekBar seekBar = findViewById(R.id.sensitivitySeekbar);
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        int progress = seekBar.getProgress();
        sensitivityLabel = findViewById(R.id.sensitivityLabel);
        sensitivityLabel.setText("" + String.valueOf(progress));

        currDistance = (TextView)findViewById(R.id.currDistance);
        calibration = (TextView)findViewById(R.id.calibration);
        // Instantiate the RequestQueue.
        final RequestQueue queue = Volley.newRequestQueue(this);
        final GraphView graph = (GraphView) findViewById(R.id.graph);
        String url ="http://10.0.0.43:3000/status";
        final DataPoint[] series = new DataPoint[nrDataPoints];
        final MaterialButton calibrate = (MaterialButton) findViewById(R.id.calibrate);
        calibrate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Double refDistance = dataset[dataset.length-1];
                String distanceText = (String) currDistance.getText();
                String distance = distanceText.split("Distance: ")[1];
                minDistance = Double.parseDouble(distance);
                calibration.setText("Calibration: " + distance);
            }
        });

        // Define graph properties
        final Double maxY = 25.0;
        graph.setTitle("Posture Pal Monitor");
        graph.getViewport().setMaxX(nrDataPoints);
        graph.getViewport().setMinY(10.0);
        graph.getViewport().setMaxY(maxY);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);

        // Notification
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "NotificationID")
                .setSmallIcon(R.drawable.star)
                .setContentTitle("Posture Pal")
                .setContentText("Sit up")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true);
        createNotificationChannel();

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, mBuilder.build()); // testing
        // Instantiate JsonObjectRequest
        final JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        Double distance = 0.;
                        try {
                            distance = response.getDouble("status");
                            dataset = updateData(dataset, distance);

                            for (int i = 0; i < dataset.length; i++) {
                                if (dataset[i] == null) {
                                    dataset[i] = 0.0;
                                }
                                if (dataset[i] > maxY) {
                                    dataset[i] = dataset[i - 1];
                                } else if (dataset[i] < minDistance){
                                }
                                series[i] = new DataPoint(i, dataset[i]);
                            }

                            if (distance < minDistance) {
                                Log.i("POSTURE", distance.toString() + " " + minDistance.toString());
                                // notificationId is a unique int for each notification that you must define
                                notificationManager.notify(1, mBuilder.build());
                            }

                            LineGraphSeries<DataPoint> data = new LineGraphSeries<>(series);

                            // Update graph
                            graph.removeAllSeries();
                            graph.addSeries(data);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        currDistance.setText("Distance: " + String.format("%.2f", distance));
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO: Handle error
                        currDistance.setText(error.getMessage());
                    }
                });
        queue.add(jsonObjectRequest);

        // repeat request every second
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                queue.add(jsonObjectRequest);
            }
        }, 0, 1000);
    }


//    public void sendNotification(View view) {
//        Log.i("NOTIFICATION", "Button pressed");
//        NotificationCompat.Builder mBuilder =
//                new NotificationCompat.Builder(this);
//
//        //Create the intent that’ll fire when the user taps the notification//
//
//        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.androidauthority.com/"));
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
//
//        mBuilder.setContentIntent(pendingIntent);
//
//        mBuilder.setSmallIcon(R.drawable.star);
//        mBuilder.setContentTitle("My notification");
//        mBuilder.setContentText("Hello World!");
//
//        NotificationManager mNotificationManager =
//
//                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//        mNotificationManager.notify(001, mBuilder.build());
//    }

    private Double[] updateData(Double[] dataset, Double distance) {
        Log.i("STAT", distance.toString());
        // Check for uninitialized entries
        for (int i=0; i < dataset.length; i++) {
            if (dataset[i] == null) {
                dataset[i] = distance;
                break;
            }
            if (i == dataset.length - 1) {
                for (int j = 0; j < dataset.length; j++) {
                    if (j == dataset.length - 1)
                    {
                        dataset[j] = distance;
                    } else {
                        dataset[j] = dataset[j+1];
                    }
                }
            }
        }
        return dataset;
    }

    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // updated continuously as the user slides the thumb
            sensitivityLabel.setText("" + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // called when the user first touches the SeekBar
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // called after the user finishes moving the SeekBar
        }
    };

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("NotificationID", name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
