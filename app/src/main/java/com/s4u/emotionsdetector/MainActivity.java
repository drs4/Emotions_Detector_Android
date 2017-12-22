package com.s4u.emotionsdetector;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.MPPointF;
import com.google.gson.Gson;
import com.s4u.emotionsdetector.http.FileUploadCallback;
import com.s4u.emotionsdetector.http.HttpAPI;
import com.s4u.emotionsdetector.models.FaceEmotion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements OnChartValueSelectedListener {

    private static int RESULT_LOAD_IMAGE = 1;
    private static final int CAMERA_REQUEST = 2;
    public HttpAPI api;

    public Gson gson;

    @Bind(R.id.imgMain) ImageView imgMain;
    @Bind(R.id.fab) FloatingActionButton fab;
    @Bind(R.id.pieChart) PieChart pieChart;
    @Bind(R.id.loadingBar) ProgressBar loadingBar;
    @Bind(R.id.uploadingBar) ProgressBar uploadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        api = new HttpAPI(this,"",30000,true,false);
        gson = new Gson();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.mipmap.ic_too_sad);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
                if(imgMain.getTag()==null)
                {
                    toast(getString(R.string.invalid_file));
                    return;
                }
                getEmotions((String)imgMain.getTag());
            }
        });

        imgMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(intent, RESULT_LOAD_IMAGE);
            }
        });
    }

    private void getEmotions(String path)
    {
        File file = new File(path);
        HashMap<String,String> headers = new HashMap<>();
        headers.put("Ocp-Apim-Subscription-Key","8c7684b6f4e448aab05e87145b498b96");
        loadingBar.setVisibility(View.VISIBLE);
        api.postFile("https://westus.api.cognitive.microsoft.com/emotion/v1.0/recognize?", file,headers, new FileUploadCallback() {
            @Override
            public void onProgress(Integer percentage) {
                uploadingBar.setProgress(percentage);
            }

            @Override
            public void onFinished(String response, HashMap<String, String> headers) {
                FaceEmotion[] fe = gson.fromJson(response,FaceEmotion[].class);
                if(fe.length < 1)
                    toast(getString(R.string.no_face));
                else
                    renderPieChart1(fe[0]);

                loadingBar.setVisibility(View.GONE);
            }

            @Override
            public void onProblem(HttpAPI.ErrorMessage errorMessage) {
                snack(errorMessage);
                loadingBar.setVisibility(View.GONE);
            }
        });

    }

    public void renderPieChart1(FaceEmotion faceEmotion)
    {
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 5);

        pieChart.setDragDecelerationFrictionCoef(0.95f);

        pieChart.setCenterText(getString(R.string.your_emotions));

        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);

        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);

        pieChart.setHoleRadius(58f);
        pieChart.setTransparentCircleRadius(61f);

        pieChart.setDrawCenterText(true);

        pieChart.setRotationAngle(0);
        // enable rotation of the chart by touch
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);

        // mChart.setUnit(" €");
        // mChart.setDrawUnitsInChart(true);

        // add a selection listener
        pieChart.setOnChartValueSelectedListener(this);

        setData(faceEmotion);

        pieChart.animateY(1400, Easing.EasingOption.EaseInOutQuad);
        // mChart.spin(2000, 0, 360);


        Legend l = pieChart.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        l.setOrientation(Legend.LegendOrientation.VERTICAL);
        l.setDrawInside(false);
        l.setXEntrySpace(7f);
        l.setYEntrySpace(0f);
        l.setYOffset(0f);

        // entry label styling
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setEntryLabelTextSize(12f);

    }

    private void setData(FaceEmotion faceEmotion) {


        ArrayList<PieEntry> entries = new ArrayList<PieEntry>();

        // NOTE: The order of the entries when being added to the entries array determines their position around the center of
        // the chart.

        entries.add(new PieEntry((float)faceEmotion.scores.happiness,getString(R.string.happiness)));

        entries.add(new PieEntry((float)faceEmotion.scores.sadness,getString(R.string.sadness)));

        entries.add(new PieEntry((float)faceEmotion.scores.anger,getString(R.string.anger)));

        entries.add(new PieEntry((float)faceEmotion.scores.disgust,getString(R.string.disgust)));

        entries.add(new PieEntry((float)faceEmotion.scores.fear,getString(R.string.fear)));

        entries.add(new PieEntry((float)faceEmotion.scores.surprise,getString(R.string.surprise)));

        entries.add(new PieEntry((float)faceEmotion.scores.neutral,getString(R.string.neutral)));

        entries.add(new PieEntry((float)faceEmotion.scores.contempt,getString(R.string.contempt)));

        Collections.sort(entries,new Comparator<PieEntry>() {
            @Override
            public int compare(PieEntry o1, PieEntry o2) {
                if(o1.getValue() > o2.getValue()) return 1;
                if(o1.getValue() < o2.getValue()) return -1;
                return 0;
            }
        });



        for(int i=0;i<4;i++)
        {
            entries.remove(0);//remove the smallest 3 entries
        }


        if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.happiness))){
            getSupportActionBar().setIcon(R.mipmap.ic_happy);

        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.sadness))){
            getSupportActionBar().setIcon(R.mipmap.ic_sadness);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.anger))){
            getSupportActionBar().setIcon(R.mipmap.ic_anger);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.surprise))){
            getSupportActionBar().setIcon(R.mipmap.ic_surprise);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.disgust))){
            getSupportActionBar().setIcon(R.mipmap.ic_disgust);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.contempt))){
            getSupportActionBar().setIcon(R.mipmap.ic_contempt);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.neutral))){
            getSupportActionBar().setIcon(R.mipmap.ic_natural);
        }
        else if(entries.get(entries.size()-1).getLabel().equals(getString(R.string.fear))){
            getSupportActionBar().setIcon(R.mipmap.ic_fear);
        }

        PieDataSet dataSet = new PieDataSet(entries, getString(R.string.emotions_result));

        dataSet.setDrawIcons(false);

        dataSet.setSliceSpace(3f);
        dataSet.setIconsOffset(new MPPointF(0, 40));
        dataSet.setSelectionShift(5f);

        // add a lot of colors

        ArrayList<Integer> colors = new ArrayList<Integer>();


        for (int c : ColorTemplate.MY_COLORS)
            colors.add(c);


        colors.add(ColorTemplate.getHoloBlue());

        dataSet.setColors(colors);
        //dataSet.setSelectionShift(0f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.WHITE);

        pieChart.setData(data);

        // undo all highlights
        pieChart.highlightValues(null);

        pieChart.invalidate();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            if(getLocal().equals("en"))
            {
                setLocale("ar",true);
            }
            else
            {
                setLocale("en",true);
            }
            return true;
        }
        if(id == R.id.action_select){
            imgMain.callOnClick();
            return true;
        }
        if(id == R.id.action_picture)
        {
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            imgMain.setTag(picturePath);
            imgMain.setImageBitmap(BitmapFactory.decodeFile(picturePath));

        }
        else if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");



            FileOutputStream out = null;
            try {
                out = new FileOutputStream("/sdcard/img.png");
                photo.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if(out != null)
            {
                imgMain.setTag("/sdcard/img.png");
            }

            imgMain.setImageBitmap(photo);
        }
    }

    public void setLocale(String lang,boolean start) {

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration();

        config.locale = locale;
        getBaseContext().getResources().updateConfiguration(config, null);
        Resources.getSystem().updateConfiguration(config, null);

        if(start)
        {
            Intent refresh = new Intent(this, MainActivity.class);
            startActivity(refresh);
            finish();
        }
    }
    public String getLocal() {

        Locale local = Locale.getDefault();
        return local.getLanguage();
    }


    public void snack(String message)
    {
        Snackbar.make(findViewById(R.id.frmContent), message, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    public void snack(HttpAPI.ErrorMessage message)
    {
        Snackbar.make(findViewById(R.id.frmContent), getString(R.string.message_check_internet), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    public void toast(String message)
    {
        Toast.makeText(this,message, Toast.LENGTH_LONG).show();
    }

    public void log(String log)
    {
        Log.d("Debug",getClass().getSimpleName()+": "+log);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {

    }

    @Override
    public void onNothingSelected() {

    }
}
