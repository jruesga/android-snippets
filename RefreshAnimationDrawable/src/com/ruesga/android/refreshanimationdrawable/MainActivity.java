package com.ruesga.android.refreshanimationdrawable;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
    private boolean state = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TypedArray ta = obtainStyledAttributes(new int[]{android.R.attr.colorControlActivated});
        int color = ta.getColor(0, Color.WHITE);
        ta.recycle();

        final RefreshAnimationView refresh = (RefreshAnimationView)  findViewById(R.id.image);
        refresh.setColor(color);
        final Button run = (Button) findViewById(R.id.button);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!state) {
                    refresh.startProgress();
                    run.setText("Stop");
                } else {
                    refresh.stopProgress();
                    run.setText("Run");
                }
                state = !state;
            }
        });
    }
}
