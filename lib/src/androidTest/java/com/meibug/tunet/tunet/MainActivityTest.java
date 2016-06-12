package com.meibug.tunet.tunet;

import android.app.Activity;
import android.support.v4.BuildConfig;
import android.widget.Button;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import cn.nekocode.kotgo.sample.data.R;

/**
 * Created by xing on 16/6/2.
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class MainActivityTest {

    @Test
    public void clickingButton_shouldChangeResultsViewText() throws Exception {
/*        Activity activity = Robolectric.setupActivity(MainActivity.class);

        Button button = (Button) activity.findViewById(R.id.press_me_button);
        TextView results = (TextView) activity.findViewById(R.id.results_text_view);

        button.performClick();
        assertTrue(results.getText().toString(), equalTo("Testing Android Rocks!"));*/
    }
}
