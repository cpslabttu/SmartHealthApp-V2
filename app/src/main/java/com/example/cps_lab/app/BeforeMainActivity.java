package com.example.cps_lab.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.cps_lab.R;

import java.io.IOException;

public class BeforeMainActivity extends AppCompatActivity {

    Button logIn, signUp, guestAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_before_main);

        logIn= (Button) findViewById(R.id.login);
        signUp= (Button) findViewById(R.id.signup);
        guestAccess= (Button) findViewById(R.id.guestaccess);
        guestAccess.setVisibility(View.GONE);

        /*try {
            PreTrainedModelforArrythmiaDetection model = new PreTrainedModelforArrythmiaDetection(this, "ANN_Classifier.tflite");
//            float[] inputData = new float[]{
//                    0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f,
//                    1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f,
//                    2.1f, 2.2f, 2.3f, 2.4f, 2.5f, 2.6f, 2.7f, 2.8f, 2.9f, 3.0f,
//                    3.1f, 3.2f, 3.3f, 3.4f, 3.5f, 3.6f, 3.7f, 3.8f, 3.9f, 4.0f,
//                    4.1f, 4.2f, 4.3f, 4.4f, 4.5f, 4.6f, 4.7f, 4.8f, 4.9f, 5.0f,
//                    5.1f, 5.2f, 5.3f, 5.4f, 5.5f, 5.6f, 5.7f, 5.8f, 5.9f, 6.0f,
//                    6.1f, 6.2f, 6.3f, 6.4f, 6.5f, 6.6f, 6.7f, 6.8f, 6.9f, 7.0f,
//                    7.1f, 7.2f, 7.3f, 7.4f, 7.5f, 7.6f, 7.7f, 7.8f, 7.9f, 8.0f,
//                    8.1f, 8.2f, 8.3f, 8.4f, 8.5f, 8.6f, 8.7f, 8.8f, 8.9f, 9.0f,
//                    9.1f, 9.2f, 9.3f, 9.4f, 9.5f, 9.6f, 9.7f, 9.8f, 9.9f, 10.0f,
//                    10.1f, 10.2f, 10.3f, 10.4f, 10.5f, 10.6f, 10.7f, 10.8f, 10.9f, 11.0f,
//                    11.1f, 11.2f, 11.3f, 11.4f, 11.5f, 11.6f, 11.7f, 11.8f, 11.9f, 12.0f,
//                    12.1f, 12.2f, 12.3f, 12.4f, 12.5f, 12.6f, 12.7f, 12.8f, 12.9f, 13.0f,
//                    13.1f, 13.2f, 13.3f, 13.4f, 13.5f, 13.6f, 13.7f, 13.8f, 13.9f, 14.0f,
//                    14.1f, 14.2f, 14.3f, 14.4f, 14.5f, 14.6f, 14.7f, 14.8f, 14.9f, 15.0f,
//                    15.1f, 15.2f, 15.3f, 15.4f, 15.5f, 15.6f, 15.7f, 15.8f, 15.9f, 16.0f
//            };
            float[] inputData = new float[3000];
            int predictClasss = model.predictArrhythmiaClass(inputData, this, "ANN_Classifier.tflite");
            Log.d("PredictClasss         ", String.valueOf(predictClasss));

        } catch (IOException e) {
            e.printStackTrace();
        }*/


        logIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(BeforeMainActivity.this, LogInActivity.class);
                startActivity(intent);
            }
        });
        signUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(BeforeMainActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
        guestAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(BeforeMainActivity.this, AfterLoginActivity.class);
                startActivity(intent);
            }
        });
    }
}