package com.choicely.imagecompare.settings;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatCheckBox;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.choicely.imagecompare.BaseActivity;
import com.choicely.imagecompare.R;
import com.choicely.imagecompare.fresco.FrescoListActivity;
import com.choicely.imagecompare.list.ImageListActivity;
import com.choicely.imagecompare.util.ImageUtil;
import com.choicely.imagecompare.util.ImageUtil.ImageServiceProvider;
import com.choicely.imagecompare.util.QLog;

/**
 * Created by Tommy on 21/01/16.
 */
public class SettingsActivity extends BaseActivity {

    private final String TAG = "SettingsActivity";
    private final ImageUtil imageUtil = ImageUtil.getInstance();

    private RadioGroup radioGroup;
    private EditText sampleSizeEdit;
    private AppCompatCheckBox blurCheck;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        blurCheck = findViewById(R.id.settings_blur_checkbox);
        blurCheck.setChecked(imageUtil.isBlur());
        sampleSizeEdit = findViewById(R.id.settings_sample_size_edit);
        Integer sampleSize = imageUtil.getImageSampleSize();
        if (sampleSize != null && sampleSize > 0) {
            sampleSizeEdit.setText(Integer.toString(sampleSize));
        }
        radioGroup = findViewById(R.id.settings_radio_group);
        ImageServiceProvider provider = imageUtil.getProvider();
        QLog.d(TAG, "Current provider: %s", provider);
        radioGroup.check(provider.checkBoxID);

        radioGroup.setOnCheckedChangeListener(onProviderChange);

    }

    private OnCheckedChangeListener onProviderChange = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            for (ImageServiceProvider provider : ImageServiceProvider.values()) {
                if (provider.checkBoxID == checkedId) {
                    imageUtil.setProvider(provider);
                    return;
                }
            }

        }
    };

    public void onStartClick(View view) {
        int sampleSize = 0;
        try {
            String sampleSizeString = sampleSizeEdit.getText().toString();
            if (!TextUtils.isEmpty(sampleSizeString)) {
                sampleSize = Integer.parseInt(sampleSizeString);
            } else {
                QLog.d(TAG, "Sample size empty, reset to zero");
            }
        } catch (Exception e) {
            QLog.w(e, TAG, "Problem getting sample size");
        }
        imageUtil.setImageSamplseSize(sampleSize);
        imageUtil.setBlur(blurCheck.isChecked());

        switch (imageUtil.getProvider()) {
            case FRESCO:
                startActivity(new Intent(this, FrescoListActivity.class));
                break;
            default:
                startActivity(new Intent(this, ImageListActivity.class));
                break;
        }
    }

}
