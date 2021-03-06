package com.choicely.imagecompare.list;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.choicely.imagecompare.BaseActivity;
import com.choicely.imagecompare.R;
import com.choicely.imagecompare.util.ImageUtil;

public class ImageListActivity extends BaseActivity {

    private ImageUtil imageUtil = ImageUtil.getInstance();
    private RecyclerView recyclerView;
    private ImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        setTitle(imageUtil.getProvider().toString());
        recyclerView = (RecyclerView) findViewById(R.id.main_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        updateContent();
    }

    private void updateContent() {
        if(adapter == null) {
            adapter = new ImageAdapter();
            recyclerView.setAdapter(adapter);
        }
        adapter.addAll(imageUtil.getImageList());
        adapter.notifyDataSetChanged();

    }

}
