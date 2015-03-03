package com.ruesga.android.resultanimationdrawable;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;


public class MainActivity extends Activity {

    private ResultAnimationDrawable.STATE state = ResultAnimationDrawable.STATE.SUCCESS;

    private ResultAnimationDrawable mDw;
    private Button mButton1;
    private ImageView mView;
    private SeekBar mSeekBar1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDw = new ResultAnimationDrawable();
        mDw.setColors(getResources().getColor(android.R.color.holo_green_dark),
                getResources().getColor(android.R.color.holo_red_dark));
        mView = (ImageView) findViewById(R.id.image);
        mView.setImageDrawable(mDw);

        mButton1 = (Button) findViewById(R.id.button1);
        mButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == ResultAnimationDrawable.STATE.SUCCESS) {
                    state = ResultAnimationDrawable.STATE.ERROR;
                } else {
                    state = ResultAnimationDrawable.STATE.SUCCESS;
                }
                setState(state);
            }
        });

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDw.start();
            }
        });

        mSeekBar1 = (SeekBar) findViewById(R.id.interpolation);
        mSeekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mDw.setInterpolation(progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        setState(ResultAnimationDrawable.STATE.SUCCESS);
    }

    private void setState(ResultAnimationDrawable.STATE state) {
        mDw.setState(state);
        mDw.setInterpolation(1.0f);
        mSeekBar1.setProgress(100);
        mButton1.setText(state.toString());
    }
}
