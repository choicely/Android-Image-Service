package com.choicely.imagecompare.fresco;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.choicely.imagecompare.BaseActivity;
import com.choicely.imagecompare.R;
import com.choicely.imagecompare.util.ImageUtil;

public class FrescoListActivity extends BaseActivity {

    private ImageUtil imageUtil = ImageUtil.getInstance();
    private RecyclerView recyclerView;
    private FrescoImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        recyclerView = (RecyclerView) findViewById(R.id.main_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        updateContent();
    }

    private void updateContent() {
        if(adapter == null) {
            adapter = new FrescoImageAdapter();
            recyclerView.setAdapter(adapter);
        }
        adapter.addAll(imageUtil.getImageList());
        adapter.notifyDataSetChanged();

    }

}
